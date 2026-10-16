package ru.dndmemory.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import java.util.UUID;

/**
 * Корень цепочки классов справочника: у любого объекта есть идентификатор и кампания.
 *
 * <p>Цепочка целиком: {@link GameEntityExtender} → {@link NamedExtender} →
 * {@link SyncExtender} → {@link ImageExtender}, листовые классы ({@link Hero},
 * {@link Character}, {@link Location}, {@link Item}) — обычные {@code @Entity} со своей
 * таблицей. Каждый класс лежит в своей таблице, поэтому новое поле типа не затрагивает
 * остальные (docs/redesign-v2.md, раздел 2).</p>
 */
@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
public abstract class GameEntityExtender {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;
}
