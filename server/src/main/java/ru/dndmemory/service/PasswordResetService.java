package ru.dndmemory.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dndmemory.config.AppProperties;
import ru.dndmemory.domain.PasswordResetRequest;
import ru.dndmemory.domain.User;
import ru.dndmemory.repo.PasswordResetRepository;
import ru.dndmemory.repo.RefreshTokenRepository;
import ru.dndmemory.repo.UserRepository;
import ru.dndmemory.web.ApiException;
import ru.dndmemory.web.dto.AdminDtos;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Сброс пароля без SMTP: почтовый сервер не настроен, поэтому ссылка пишется в лог
 * сервера, а ожидающие запросы видны администратору (docs/redesign-v2.md, раздел 1).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    /** Токен живёт час: за это время ссылку успевают передать. */
    private static final long TOKEN_HOURS = 1;

    private final UserRepository userRepository;
    private final PasswordResetRepository passwordResetRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final AppProperties properties;

    /**
     * Всегда завершается успешно: по ответу нельзя узнать, есть ли такой адрес.
     * Если пользователь есть — создаётся одноразовый токен и пишется ссылка в лог.
     */
    @Transactional
    public void request(String email) {
        if (email == null || email.trim().isEmpty()) {
            return;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        userRepository.findByEmailIgnoreCase(normalized).ifPresent(user -> {
            Instant requestedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            String token = TokenUtils.passwordResetToken(properties.getJwt().getSecret(),
                    user.getId(), requestedAt);

            PasswordResetRequest request = new PasswordResetRequest();
            request.setId(UUID.randomUUID());
            request.setUserId(user.getId());
            request.setTokenHash(TokenUtils.sha256(token));
            request.setExpiresAt(requestedAt.plus(TOKEN_HOURS, ChronoUnit.HOURS));
            request.setUsed(false);
            request.setCreatedAt(requestedAt);
            passwordResetRepository.save(request);

            log.warn("СБРОС ПАРОЛЯ: login={} email={} ссылка={}/#/reset/{}",
                    user.getLogin(), user.getEmail(), trimSlash(properties.getPublicUrl()), token);
        });
    }

    /** Новый пароль по токену; все сессии пользователя гасятся. */
    @Transactional
    public void confirm(String token, String newPassword) {
        PasswordResetRequest request = passwordResetRepository
                .findByTokenHash(TokenUtils.sha256(token == null ? "" : token.trim()))
                .orElseThrow(ApiException::resetInvalid);
        if (!request.isValid()) {
            throw ApiException.resetInvalid();
        }
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(ApiException::resetInvalid);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        request.setUsed(true);
        passwordResetRepository.save(request);
        refreshTokenRepository.revokeAllForUser(user.getId());
    }

    /** Список ожидающих сбросов для админ-панели. */
    @Transactional(readOnly = true)
    public List<AdminDtos.PasswordResetDto> pending() {
        List<AdminDtos.PasswordResetDto> result = new ArrayList<>();
        for (PasswordResetRequest request : passwordResetRepository
                .findAllByUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(Instant.now())) {
            User user = userRepository.findById(request.getUserId()).orElse(null);
            if (user == null) {
                continue;
            }
            result.add(AdminDtos.PasswordResetDto.builder()
                    .id(request.getId())
                    .login(user.getLogin())
                    .email(user.getEmail())
                    .token(TokenUtils.passwordResetToken(properties.getJwt().getSecret(),
                            user.getId(), request.getCreatedAt()))
                    .requestedAt(request.getCreatedAt())
                    .expiresAt(request.getExpiresAt())
                    .build());
        }
        return result;
    }

    /** Токен, который сейчас действует для пользователя (для тестов и лога). */
    public String tokenFor(PasswordResetRequest request) {
        return TokenUtils.passwordResetToken(properties.getJwt().getSecret(),
                request.getUserId(), request.getCreatedAt());
    }

    private String trimSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
