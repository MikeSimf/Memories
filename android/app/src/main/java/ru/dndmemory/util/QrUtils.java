package ru.dndmemory.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import androidx.annotation.Nullable;

/** Разбор QR-кодов приглашений: картинка из base64 и токен из отсканированного текста. */
public final class QrUtils {

    private QrUtils() {
    }

    /** Сервер отдаёт PNG в base64 (docs/api.md, раздел 5). */
    @Nullable
    public static Bitmap decodeBase64Png(@Nullable String base64) {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Достаёт токен приглашения из отсканированного текста.
     * Поддерживаются {@code dndmemory://join?token=…}, ссылка {@code /join/TOKEN} и сам токен.
     */
    @Nullable
    public static String extractToken(@Nullable String scanned) {
        if (scanned == null) {
            return null;
        }
        String value = scanned.trim();
        if (value.isEmpty()) {
            return null;
        }
        int index = value.indexOf("token=");
        if (index >= 0) {
            String tail = value.substring(index + "token=".length());
            int amp = tail.indexOf('&');
            if (amp >= 0) {
                tail = tail.substring(0, amp);
            }
            return normalize(tail);
        }
        int join = value.indexOf("/join/");
        if (join >= 0) {
            return normalize(value.substring(join + "/join/".length()));
        }
        return normalize(value);
    }

    @Nullable
    private static String normalize(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String token = raw.trim();
        // Токен — 8 символов A–Z0–9 (docs/roles.md, раздел 3).
        if (token.isEmpty()) {
            return null;
        }
        return token.toUpperCase(java.util.Locale.ROOT);
    }
}
