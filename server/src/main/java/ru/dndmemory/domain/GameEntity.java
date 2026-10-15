package ru.dndmemory.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Объект справочника: герой, персонаж, локация или предмет.
 * Все типы живут в одной таблице — так одинаково работают ссылки, поиск и синхронизация,
 * а различия лежат в attrs (см. docs/api.md, раздел 6).
 */
@Entity
@Table(name = "entities")
@Getter
@Setter
@NoArgsConstructor
public class GameEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    /** Идентификатор создания: защищает от дублей при офлайн-создании на двух устройствах. */
    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EntityType type;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(columnDefinition = "text")
    private String description;

    /** JSON-объект с полями конкретного типа. Пишется как строка (stringtype=unspecified). */
    @Column(columnDefinition = "jsonb", nullable = false)
    private String attrs = "{}";

    /** JSON-массив строк. */
    @Column(columnDefinition = "jsonb", nullable = false)
    private String tags = "[]";

    @Column(name = "image_file_id")
    private UUID imageFileId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(nullable = false)
    private long revision;

    @Column(nullable = false)
    private boolean deleted;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
