package ru.dndmemory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dndmemory.config.AppProperties;
import ru.dndmemory.domain.RefreshToken;
import ru.dndmemory.domain.User;
import ru.dndmemory.repo.RefreshTokenRepository;
import ru.dndmemory.repo.UserRepository;
import ru.dndmemory.security.JwtService;
import ru.dndmemory.web.ApiException;
import ru.dndmemory.web.dto.AuthDtos;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Вход по логину и паролю. Внешних провайдеров больше нет; приложение получает
 * долгую сессию, передав {@code platform: "android"} в запросе на вход или регистрацию.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties properties;
    private final DtoMapper dtoMapper;
    private final InviteService inviteService;

    @Transactional
    public AuthDtos.TokenResponse register(AuthDtos.RegisterRequest request) {
        String login = normalizeLogin(request.getLogin());
        if (userRepository.existsByLogin(login)) {
            throw ApiException.loginTaken();
        }
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.emailTaken();
        }

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setLogin(login);
        user.setEmail(email);
        user.setDisplayName(request.getDisplayName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmailVerified(false);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        // Приглашение из QR: пользователь сразу становится участником кампании,
        // приглашение при этом гасится (одноразовое).
        if (request.getInviteToken() != null && !request.getInviteToken().trim().isEmpty()) {
            inviteService.accept(request.getInviteToken(), user.getId());
        }
        return issueTokens(user, request.getPlatform(), null);
    }

    @Transactional
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
        User user = findByLogin(request.getLogin())
                .orElseThrow(() -> ApiException.unauthorized("Неверный логин или пароль"));
        if (!user.hasPassword() || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Неверный логин или пароль");
        }
        return issueTokens(user, request.getPlatform(), null);
    }

    /** Логин хранится в нижнем регистре — регистр при входе не важен. */
    @Transactional(readOnly = true)
    public Optional<User> findByLogin(String login) {
        if (login == null || login.trim().isEmpty()) {
            return Optional.empty();
        }
        return userRepository.findByLogin(login.trim().toLowerCase(Locale.ROOT));
    }

    @Transactional
    public AuthDtos.TokenResponse refresh(String refreshToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(TokenUtils.sha256(refreshToken))
                .orElseThrow(() -> ApiException.unauthorized("Сессия истекла, войдите заново"));
        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.unauthorized("Сессия истекла, войдите заново");
        }
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("Пользователь не найден"));
        return issueTokens(user, stored.getPlatform(), stored.getUserAgent());
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByTokenHash(TokenUtils.sha256(refreshToken))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
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
        userRepository.save(user);
        return user;
    }

    /** Смена пароля из профиля: текущий пароль спрашиваем, потому что он всегда есть. */
    @Transactional
    public void setPassword(UUID userId, AuthDtos.SetPasswordRequest request) {
        User user = requireUser(userId);
        if (user.hasPassword() && (request.getCurrentPassword() == null
                || !passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash()))) {
            throw ApiException.unauthorized("Текущий пароль указан неверно");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }

    public long accessTokenTtlSeconds() {
        return jwtService.getAccessTtlSeconds();
    }

    /** Логин: 3–32 символа [a-z0-9_.-], хранится в нижнем регистре. */
    public static String normalizeLogin(String login) {
        if (login == null) {
            throw ApiException.validation("Укажите логин");
        }
        String value = login.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z0-9_.\\-]{3,32}")) {
            throw ApiException.validation("Логин: 3–32 символа из a–z, 0–9, _ . -");
        }
        return value;
    }

    private AuthDtos.TokenResponse issueTokens(User user, String platform, String userAgent) {
        boolean android = AuthDtos.PLATFORM_ANDROID.equalsIgnoreCase(platform == null ? "" : platform.trim());
        int refreshDays = android
                ? properties.getJwt().getAndroidRefreshTokenDays()
                : properties.getJwt().getRefreshTokenDays();

        String accessToken = jwtService.createAccessToken(user);
        String refreshValue = TokenUtils.newRefreshToken();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(UUID.randomUUID());
        refreshToken.setUserId(user.getId());
        refreshToken.setTokenHash(TokenUtils.sha256(refreshValue));
        refreshToken.setPlatform(android ? AuthDtos.PLATFORM_ANDROID : "web");
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

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
