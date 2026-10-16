package ru.dndmemory.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.UUID;

/**
 * Предмет в котомке героя. Отдельная таблица {@code hero_items}: список маленький и
 * всегда едет вместе с героем (docs/redesign-v2.md, раздел 2).
 *
 * <p>{@code itemId} заполняется, если название нашлось среди предметов кампании —
 * сопоставление по имени, как у ссылок {@code [[…]]}.</p>
 */
@Entity
@Table(name = "hero_items")
@Getter
@Setter
@NoArgsConstructor
public class HeroItem {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "hero_id", nullable = false)
    private UUID heroId;

    @Column(nullable = false, length = 200)
    private String name;

    /** Ссылка на предмет кампании, если название совпало. */
    @Column(name = "item_id")
    private UUID itemId;

    private Integer quantity;

    @Column(columnDefinition = "text")
    private String notes;

    /** Порядок в списке: как мастер расставил предметы. */
    @Column(nullable = false)
    private int position;
}
