package ru.dndmemory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Пуш приходит батчем одной транзакцией: исключение на одном поле откатывает все
 * правки клиента, и побитая правка повторяется при каждом соединении. Поэтому
 * неразбираемые значения должны читаться как пустые, а не бросать.
 */
class PayloadValuesTest {

    private final ObjectMapper json = new ObjectMapper();

    private JsonNode node(String raw) throws Exception {
        return json.readTree(raw);
    }

    @Test
    @DisplayName("Пустая строка вместо даты означает «дату сняли», а не ошибку")
    void treatsEmptyDateAsNull() throws Exception {
        assertNull(PayloadValues.date(node("\"\"")));
        assertNull(PayloadValues.date(node("\"   \"")));
        assertNull(PayloadValues.date(null));
        assertNull(PayloadValues.date(node("null")));
    }

    @Test
    @DisplayName("Корректная дата разбирается, мусорная — даёт null без исключения")
    void parsesAndIgnoresDates() throws Exception {
        assertEquals(LocalDate.of(2026, 11, 12), PayloadValues.date(node("\"2026-11-12\"")));
        assertEquals(LocalDate.of(2026, 11, 12), PayloadValues.date(node("\" 2026-11-12 \"")));
        assertNull(PayloadValues.date(node("\"не дата\"")));
        assertNull(PayloadValues.date(node("\"2026-13-45\"")));
    }

    @Test
    @DisplayName("Идентификатор автора: пустой и мусорный читаются как «не задан»")
    void readsOptionalUuidWithoutThrowing() throws Exception {
        UUID id = UUID.randomUUID();
        assertEquals(id, PayloadValues.optionalUuid(node("\"" + id + "\"")));
        assertNull(PayloadValues.optionalUuid(node("\"\"")));
        assertNull(PayloadValues.optionalUuid(node("\"не uuid\"")));
        assertNull(PayloadValues.optionalUuid(node("null")));
        assertNull(PayloadValues.optionalUuid(null));
    }

    @Test
    @DisplayName("Метка времени: битое значение даёт запасное, а не падение пуша")
    void fallsBackOnInstant() throws Exception {
        Instant fallback = Instant.ofEpochMilli(1_700_000_000_000L);
        JsonNode payload = node("{\"createdAt\":\"2026-11-12T10:00:00Z\",\"updatedAt\":\"\",\"bad\":\"х\"}");

        assertEquals(Instant.parse("2026-11-12T10:00:00Z"), PayloadValues.instant(payload, "createdAt", fallback));
        assertEquals(fallback, PayloadValues.instant(payload, "updatedAt", fallback));
        assertEquals(fallback, PayloadValues.instant(payload, "bad", fallback));
        assertEquals(fallback, PayloadValues.instant(payload, "absent", fallback));
        assertEquals(fallback, PayloadValues.instant(null, "createdAt", fallback));
    }
}
