package ru.dndmemory.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dndmemory.config.AppProperties;
import ru.dndmemory.domain.EmailVerificationToken;
import ru.dndmemory.domain.User;
import ru.dndmemory.repo.EmailVerificationRepository;
import ru.dndmemory.repo.UserRepository;
import ru.dndmemory.web.ApiException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Подтверждение почты: письмо со ссылкой и одноразовый токен. */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final long TOKEN_HOURS = 48;

    private final UserRepository userRepository;
    private final EmailVerificationRepository tokenRepository;
    private final MailService mailService;
    private final AppProperties properties;

    @Transactional
    public void send(User user) {
        if (user.getEmail() == null || user.getEmail().isEmpty()) {
            return;
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String token = TokenUtils.newOpaqueToken();
        EmailVerificationToken entity = new EmailVerificationToken();
        entity.setId(UUID.randomUUID());
        entity.setUserId(user.getId());
        entity.setTokenHash(TokenUtils.sha256(token));
        entity.setExpiresAt(now.plus(TOKEN_HOURS, ChronoUnit.HOURS));
        entity.setUsed(false);
        entity.setCreatedAt(now);
        tokenRepository.save(entity);

        String link = trimSlash(properties.getPublicUrl()) + "/#/verify-email/" + token;
        String text = "Здравствуйте, " + user.getDisplayName() + "!\n\n"
                + "Подтвердите почту в DnD Memory:\n" + link + "\n\n"
                + "Ссылка действует 48 часов. Если вы не регистрировались, просто проигнорируйте письмо.";
        boolean sent = mailService.send(user.getEmail(), "Подтверждение почты в DnD Memory", text);
        if (!sent) {
            log.warn("ПОДТВЕРЖДЕНИЕ ПОЧТЫ: {} ссылка={}", user.getEmail(), link);
        }
    }

    /** Повторная отправка письма по запросу пользователя. */
    @Transactional
    public void resend(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Пользователь не найден"));
        if (user.isEmailVerified()) {
            return;
        }
        send(user);
    }

    @Transactional
    public void verify(String token) {
        EmailVerificationToken entity = tokenRepository
                .findByTokenHash(TokenUtils.sha256(token == null ? "" : token.trim()))
                .orElseThrow(() -> ApiException.badRequest("Ссылка подтверждения недействительна"));
        if (!entity.isValid()) {
            throw ApiException.badRequest("Ссылка подтверждения истекла");
        }
        User user = userRepository.findById(entity.getUserId())
                .orElseThrow(() -> ApiException.badRequest("Пользователь не найден"));
        user.setEmailVerified(true);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        entity.setUsed(true);
        tokenRepository.save(entity);
    }

    private String trimSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
