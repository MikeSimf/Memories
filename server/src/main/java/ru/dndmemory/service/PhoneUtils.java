package ru.dndmemory.service;

/** Приведение телефона к единому виду: +7XXXXXXXXXX. */
public final class PhoneUtils {

    private PhoneUtils() {
    }

    /** Телефон ли это. Проверяем мягко: цифры, пробелы, скобки, дефисы и ведущий плюс. */
    public static boolean looksLikePhone(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || !trimmed.matches("^\\+?[0-9 ()\\-]{6,25}$")) {
            return false;
        }
        return digitsOnly(trimmed).length() >= 7;
    }

    /**
     * Приводит телефон к виду +7XXXXXXXXXX (для российских номеров) или +<цифры>.
     * Возвращает null, если номер не распознан.
     */
    public static String normalize(String value) {
        if (!looksLikePhone(value)) {
            return null;
        }
        String digits = digitsOnly(value);
        if (digits.length() == 11 && (digits.startsWith("8") || digits.startsWith("7"))) {
            return "+7" + digits.substring(1);
        }
        if (digits.length() == 10) {
            return "+7" + digits;
        }
        return "+" + digits;
    }

    private static String digitsOnly(String value) {
        return value.replaceAll("[^0-9]", "");
    }
}
