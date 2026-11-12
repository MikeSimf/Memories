package ru.dndmemory.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Чтение необязательных значений из payload синхронизации.
 *
 * <p>Телефон копит правки офлайн и присылает их батчем: пустая строка вместо даты или
 * непонятный идентификатор — это одно поле одной правки. Бросать здесь исключение
 * нельзя: оно вылетает за пределы пуша, откатывает весь батч, и правка уходит в повтор
 * при каждом соединении. Поэтому вместо исключения — пустое значение.
 */
public final class PayloadValues {

    private PayloadValues() {
    }

    /**
     * Дата сессии. Пустая строка или неразбираемый формат дают null — телефон снимает
     * дату именно так, присылая {@code "sessionDate": ""}.
     */
    public static LocalDate date(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText().trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    /** Идентификатор без исключения: мусорное значение читается как «не задан». */
    public static UUID optionalUuid(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return UUID.fromString(node.asText().trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** Метка времени из поля payload; при пустом или битом значении — запасное. */
    public static Instant instant(JsonNode payload, String field, Instant fallback) {
        if (payload == null || !payload.hasNonNull(field)) {
            return fallback;
        }
        try {
            return Instant.parse(payload.get(field).asText());
        } catch (Exception e) {
            return fallback;
        }
    }
}
