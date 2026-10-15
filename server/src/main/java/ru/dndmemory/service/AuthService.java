package ru.dndmemory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dndmemory.config.AppProperties;
import ru.dndmemory.domain.AuthProvider;
import ru.dndmemory.domain.RefreshToken;
import ru.dndmemory.domain.User;
import ru.dndmemory.repo.RefreshTokenRepository;
import ru.dndmemory.repo.UserRepository;
import ru.dndmemory.security.JwtService;
import ru.dndmemory.web.ApiException;
import ru.dndmemory.web.dto.AuthDtos;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties properties;
    private final DtoMapper dtoMapper;

    @Transactional
    public AuthDtos.TokenResponse register(AuthDtos.RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.emailTaken();
        }
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setDisplayName(request.getDisplayName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setProvider(AuthProvider.LOCAL);
        userRepository.save(user);
        return issueTokens(user, "email");
    }

    @Transactional
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> ApiException.unauthorized("Неверная почта или пароль"));
        if (user.getPasswordHash() == null) {
            throw ApiException.unauthorized("Этот аккаунт создан через Яндекс или VK — войдите тем же способом");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Неверная почта или пароль");
        }
        return issueTokens(user, "email");
    }

    @Transactional
    public AuthDtos.TokenResponse refresh(String refreshToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(sha256(refreshToken))
                .orElseThrow(() -> ApiException.unauthorized("Сессия истекла, войдите заново"));
        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.unauthorized("Сессия истекла, войдите заново");
        }
        stored.setRevoked(true);
        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("Пользователь не найден"));
        return issueTokens(user, stored.getUserAgent());
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByTokenHash(sha256(refreshToken)).ifPresent(token -> token.setRevoked(true));
    }

    /**
     * Вход через внешнего провайдера. Если почта уже есть в базе — аккаунт связывается,
     * иначе создаётся новый пользователь без пароля.
     */
    @Transactional
    public AuthDtos.TokenResponse loginWithProvider(AuthProvider provider, String providerUserId,
                                                    String email, String displayName) {
        if (providerUserId == null || providerUserId.trim().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "internal_error",
                    "Провайдер не вернул идентификатор пользователя");
        }
        Optional<User> linked = userRepository.findByProviderAndProviderUserId(provider, providerUserId);
        User user;
        if (linked.isPresent()) {
            user = linked.get();
            if (displayName != null && !displayName.trim().isEmpty()) {
                user.setDisplayName(displayName.trim());
            }
            user.setUpdatedAt(Instant.now());
        } else {
            Optional<User> byEmail = email == null ? Optional.empty() : userRepository.findByEmailIgnoreCase(email);
            if (byEmail.isPresent()) {
                user = byEmail.get();
                user.setProvider(provider);
                user.setProviderUserId(providerUserId);
                user.setUpdatedAt(Instant.now());
            } else {
                user = new User();
                user.setId(UUID.randomUUID());
                user.setEmail(email == null || email.trim().isEmpty()
                        ? provider.name().toLowerCase(Locale.ROOT) + "_" + providerUserId + "@dnd-memory.local"
                        : normalizeEmail(email));
                user.setDisplayName(displayName == null || displayName.trim().isEmpty()
                        ? "Участник" : displayName.trim());
                user.setProvider(provider);
                user.setProviderUserId(providerUserId);
                user.setCreatedAt(Instant.now());
                user.setUpdatedAt(Instant.now());
            }
        }
        userRepository.save(user);
        return issueTokens(user, provider.name().toLowerCase(Locale.ROOT));
    }

    @Transactional(readOnly = true)
    public User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Пользователь не найден"));
    }

    @Transactional
    public User updateProfile(UUID userId, AuthDtos.UpdateProfileRequest request) {
        User user = requireUser(userId);
        if (request.getDisplayName() != null && !request.getDisplayName().trim().isEmpty()) {
            user.setDisplayName(request.getDisplayName().trim());
        }
        if (request.getAvatarFileId() != null) {
            user.setAvatarFileId(request.getAvatarFileId());
        }
        user.setUpdatedAt(Instant.now());
        return user;
    }

    public AuthDtos.ProviderDto providerInfo(AppProperties.Provider provider, String name) {
        return AuthDtos.ProviderDto.builder()
                .enabled(provider != null && provider.isConfigured())
                .authorizeUrl("/api/auth/oauth/" + name + "/authorize")
                .build();
    }

    public long accessTokenTtlSeconds() {
        return jwtService.getAccessTtlSeconds();
    }

    private AuthDtos.TokenResponse issueTokens(User user, String userAgent) {
        String accessToken = jwtService.createAccessToken(user);
        String refreshValue = newRefreshToken();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(UUID.randomUUID());
        refreshToken.setUserId(user.getId());
        refreshToken.setTokenHash(sha256(refreshValue));
        refreshToken.setExpiresAt(Instant.now().plus(properties.getJwt().getRefreshTokenDays(), ChronoUnit.DAYS));
        refreshToken.setUserAgent(userAgent == null ? null
                : userAgent.substring(0, Math.min(userAgent.length(), 255)));
        refreshTokenRepository.save(refreshToken);

        return AuthDtos.TokenResponse.builder()
                .user(dtoMapper.toUser(user))
                .accessToken(accessToken)
                .refreshToken(refreshValue)
                .expiresIn(jwtService.getAccessTtlSeconds())
                .build();
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }
}
