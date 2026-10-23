package ru.dndmemory.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/** Работа с токенами: хеши, случайные значения и ссылки сброса пароля. */
public final class TokenUtils {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenUtils() {
    }

    /** SHA-256 в hex — так хранятся refresh- и reset-токены. */
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

    /** Случайный одноразовый токен: 32 байта энтропии в base64url. */
    public static String newOpaqueToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Случайный refresh-токен: 48 байт энтропии в base64url. */
    public static String newRefreshToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Токен сброса пароля выводится из секрета сервера, пользователя и времени запроса.
     *
     * <p>В базе лежит только его хеш, но администратору токен нужен целиком — поэтому
     * он не случаен, а подписан: сервер восстанавливает то же значение по строке
     * запроса. {@code requestedAt} хранится с точностью до миллисекунды, иначе
     * значение не переживёт круг через базу.</p>
     */
    public static String passwordResetToken(String secret, UUID userId, Instant requestedAt) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String message = userId + ":" + requestedAt.toEpochMilli();
            byte[] signed = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signed);
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось построить токен сброса пароля", e);
        }
    }
}
