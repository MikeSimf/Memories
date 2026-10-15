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

/** Одна запись журнала изменений кампании — единица синхронизации. */
@Entity
@Table(name = "change_log")
@Getter
@Setter
@NoArgsConstructor
public class ChangeLog {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(nullable = false)
    private long revision;

    @Enumerated(EnumType.STRING)
    @Column(name = "object_type", nullable = false, length = 20)
    private ObjectType objectType;

    @Column(name = "object_id", nullable = false)
    private UUID objectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChangeOp op;

    /** Полное состояние объекта после операции (для DELETE — надгробие). */
    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "client_change_id", length = 64)
    private String clientChangeId;

    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
