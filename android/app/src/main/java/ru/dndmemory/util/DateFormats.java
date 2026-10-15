package ru.dndmemory.util;

import androidx.annotation.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Время в API — ISO-8601 UTC с миллисекундами. В интерфейсе показываем локальное
 * время в формате {@code dd.MM.yyyy HH:mm}. java.time недоступен на minSdk 24,
 * поэтому SimpleDateFormat.
 */
public final class DateFormats {

    private static final String ISO_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final String RU_DATE = "dd.MM.yyyy";
    private static final String RU_DATE_TIME = "dd.MM.yyyy HH:mm";

    private DateFormats() {
    }

    public static String nowIso() {
        SimpleDateFormat format = new SimpleDateFormat(ISO_PATTERN, Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    public static String todayDate() {
        SimpleDateFormat format = new SimpleDateFormat(DATE_PATTERN, Locale.US);
        return format.format(new Date());
    }

    public static long parseIso(@Nullable String iso) {
        if (iso == null || iso.isEmpty()) {
            return 0L;
        }
        SimpleDateFormat format = new SimpleDateFormat(ISO_PATTERN, Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            Date date = format.parse(iso);
            return date == null ? 0L : date.getTime();
        } catch (ParseException e) {
            return 0L;
        }
    }

    /** «15.10.2025, 14:00» — для карточек и списков. */
    public static String formatIso(@Nullable String iso) {
        long millis = parseIso(iso);
        if (millis == 0L) {
            return iso == null ? "" : iso;
        }
        SimpleDateFormat format = new SimpleDateFormat(RU_DATE_TIME, Locale.getDefault());
        return format.format(new Date(millis));
    }

    /** «15.10.2025» — принимает и ISO-момент, и дату {@code yyyy-MM-dd}. */
    public static String formatDate(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.length() >= 10 && value.charAt(4) == '-' && value.charAt(7) == '-') {
            String head = value.substring(0, 10);
            try {
                SimpleDateFormat source = new SimpleDateFormat(DATE_PATTERN, Locale.US);
                source.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date = source.parse(head);
                if (date != null) {
                    return new SimpleDateFormat(RU_DATE, Locale.getDefault()).format(date);
                }
            } catch (ParseException ignored) {
                return head;
            }
        }
        long millis = parseIso(value);
        if (millis == 0L) {
            return value;
        }
        return new SimpleDateFormat(RU_DATE, Locale.getDefault()).format(new Date(millis));
    }

    /** «5 минут назад» для статуса синхронизации. */
    public static String relative(@Nullable String iso) {
        long millis = parseIso(iso);
        if (millis == 0L) {
            return "";
        }
        long diff = System.currentTimeMillis() - millis;
        if (diff < 0) {
            diff = 0;
        }
        long minutes = diff / 60000L;
        if (minutes < 1) {
            return "только что";
        }
        if (minutes < 60) {
            return minutes + " мин назад";
        }
        long hours = minutes / 60L;
        if (hours < 24) {
            return hours + " ч назад";
        }
        long days = hours / 24L;
        return days + " дн назад";
    }
}
