package ru.dndmemory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dndmemory.config.AppProperties;
import ru.dndmemory.domain.AuthProvider;
import ru.dndmemory.domain.Device;
import ru.dndmemory.domain.RefreshToken;
import ru.dndmemory.domain.User;
import ru.dndmemory.repo.DeviceRepository;
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
    private final DeviceRepository deviceRepository;
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
        User user = findByLogin(request.getEmail())
                .orElseThrow(() -> ApiException.unauthorized("Неверная почта или пароль"));
        if (!user.hasPassword()) {
            throw ApiException.unauthorized("У этого аккаунта ещё нет пароля — задайте его в профиле "
                    + "или войдите, отсканировав QR-код устройства");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Неверная почта или пароль");
        }
        return issueTokens(user, "email");
    }

    /** Вход по почте или по телефону — как удобнее. */
    @Transactional(readOnly = true)
    public Optional<User> findByLogin(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return Optional.empty();
        }
        String value = identifier.trim();
        if (PhoneUtils.looksLikePhone(value)) {
            String phone = PhoneUtils.normalize(value);
            if (phone != null) {
                Optional<User> byPhone = userRepository.findByPhone(phone);
                if (byPhone.isPresent()) {
                    return byPhone;
                }
            }
        }
        String email = normalizeEmail(value);
        return email == null ? Optional.empty() : userRepository.findByEmailIgnoreCase(email);
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
        if (stored.getDeviceId() != null) {
            Device device = deviceRepository.findById(stored.getDeviceId()).orElse(null);
            if (device == null || device.isRevoked()) {
                throw ApiException.unauthorized("Устройство отозвано администратором");
            }
            device.setLastSeenAt(Instant.now());
            deviceRepository.save(device);
            return issueTokens(user, stored.getUserAgent(), device.getId(),
                    properties.getJwt().getDeviceRefreshTokenDays());
        }
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
        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            String email = normalizeEmail(request.getEmail());
            if (!email.equals(user.getEmail()) && userRepository.existsByEmailIgnoreCase(email)) {
                throw ApiException.emailTaken();
            }
            user.setEmail(email);
        }
        if (request.getPhone() != null) {
            if (request.getPhone().trim().isEmpty()) {
                user.setPhone(null);
            } else {
                String phone = PhoneUtils.normalize(request.getPhone());
                if (phone == null) {
                    throw ApiException.validation("Не удалось разобрать номер телефона");
                }
                if (!phone.equals(user.getPhone()) && userRepository.existsByPhone(phone)) {
                    throw new ApiException(HttpStatus.CONFLICT, "phone_taken",
                            "Этот телефон уже привязан к другому аккаунту");
                }
                user.setPhone(phone);
            }
        }
        user.setUpdatedAt(Instant.now());
        return user;
    }

    /**
     * Установка или смена пароля. После этого работает вход в веб-версию по почте
     * или телефону — именно этот шаг проходят аккаунты, созданные по QR устройства.
     */
    @Transactional
    public void setPassword(UUID userId, AuthDtos.SetPasswordRequest request) {
        User user = requireUser(userId);
        if (user.hasPassword() && (request.getCurrentPassword() == null
                || !passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash()))) {
            throw ApiException.unauthorized("Текущий пароль указан неверно");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(Instant.now());
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

    /** Долгоживущая сессия для устройства, зарегистрированного по QR-коду. */
    public AuthDtos.TokenResponse issueDeviceTokens(User user, UUID deviceId, String userAgent) {
        return issueTokens(user, userAgent, deviceId, properties.getJwt().getDeviceRefreshTokenDays());
    }

    private AuthDtos.TokenResponse issueTokens(User user, String userAgent) {
        return issueTokens(user, userAgent, null, properties.getJwt().getRefreshTokenDays());
    }

    private AuthDtos.TokenResponse issueTokens(User user, String userAgent, UUID deviceId, int refreshDays) {
        String accessToken = jwtService.createAccessToken(user);
        String refreshValue = newRefreshToken();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(UUID.randomUUID());
        refreshToken.setUserId(user.getId());
        refreshToken.setTokenHash(sha256(refreshValue));
        refreshToken.setDeviceId(deviceId);
        refreshToken.setExpiresAt(Instant.now().plus(refreshDays, ChronoUnit.DAYS));
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
