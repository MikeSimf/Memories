package ru.dndmemory.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WikiLinkServiceTest {

    private final WikiLinkService service = new WikiLinkService();

    private Map<String, UUID> index(String name, UUID id) {
        Map<String, UUID> map = new HashMap<>();
        map.put(name, id);
        return map;
    }

    @Test
    @DisplayName("Ссылка по имени разрешается без учёта регистра, подпись сохраняется")
    void resolvesNameIgnoringCase() {
        UUID id = UUID.randomUUID();
        List<WikiLink> links = service.extract(index("лира ксенофонт", id),
                "Мы шли с [[Лира Ксенофонт|Лирой]] и [[Силдар Холлвинтер]].");

        assertEquals(2, links.size());
        assertTrue(links.get(0).isResolved());
        assertEquals(id, links.get(0).getTargetId());
        assertEquals("Лирой", links.get(0).getLabel());

        assertFalse(links.get(1).isResolved());
        assertNull(links.get(1).getTargetId());
        assertEquals("Силдар Холлвинтер", links.get(1).getLabel());
    }

    @Test
    @DisplayName("Ссылка по явному идентификатору [[#uuid|подпись]] разрешается всегда")
    void resolvesExplicitId() {
        UUID id = UUID.randomUUID();
        List<WikiLink> links = service.extract(index("что-то", UUID.randomUUID()),
                "Смотри [[#" + id + "|тот самый предмет]].");

        assertEquals(1, links.size());
        assertTrue(links.get(0).isResolved());
        assertEquals(id, links.get(0).getTargetId());
        assertEquals("тот самый предмет", links.get(0).getLabel());
    }

    @Test
    @DisplayName("Повтор одной и той же ссылки не дублируется")
    void deduplicates() {
        UUID id = UUID.randomUUID();
        List<WikiLink> links = service.extract(index("лира", id), "[[Лира]] и снова [[Лира]].");
        assertEquals(1, links.size());
    }

    @Test
    @DisplayName("Собирает имена, которых ещё нет в кампании")
    void collectsUnresolvedNames() {
        List<String> missing = new java.util.ArrayList<>(
                service.unresolvedNames(index("лира", UUID.randomUUID()),
                        "Нам встретился [[Силдар]] у ворот [[Фандалина]]."));
        assertEquals(2, missing.size());
        assertTrue(missing.contains("Силдар"));
        assertTrue(missing.contains("Фандалина"));
    }

    @Test
    @DisplayName("Текст без ссылок разбирается без ошибок")
    void handlesPlainText() {
        assertTrue(service.extract(new HashMap<>(), "Просто описание без ссылок.").isEmpty());
        assertTrue(service.extract(new HashMap<>(), (String[]) null).isEmpty());
    }
}
