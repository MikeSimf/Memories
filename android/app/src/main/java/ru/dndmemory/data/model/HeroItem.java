package ru.dndmemory.data.model;

/**
 * Предмет в котомке героя (docs/api.md, раздел 6, таблица {@code hero_items}).
 *
 * <p>{@code itemId} заполняется сервером, если название нашлось среди предметов
 * кампании (сопоставление по имени). На клиенте поле необязательно: предмет
 * можно записать свободной строкой.
 */
public class HeroItem {

    public String id;
    public String name;

    /** Ссылка на предмет кампании, если название с ним совпало. */
    public String itemId;

    /** Количество; {@code null} — «не указано», в JSON поле не попадает. */
    public Integer quantity;

    public String notes;

    public HeroItem() {
    }

    public HeroItem(String name) {
        this.name = name;
    }
}
