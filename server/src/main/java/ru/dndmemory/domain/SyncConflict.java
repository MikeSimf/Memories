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

/** Зафиксированный конфликт синхронизации — для разбора и диагностики. */
@Entity
@Table(name = "sync_conflicts")
@Getter
@Setter
@NoArgsConstructor
public class SyncConflict {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Enumerated(EnumType.STRING)
    @Column(name = "object_type", nullable = false, length = 20)
    private ObjectType objectType;

    @Column(name = "object_id", nullable = false)
    private UUID objectId;

    @Column(name = "client_change_id", length = 64)
    private String clientChangeId;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(nullable = false, length = 40)
    private String reason;

    @Column(name = "server_payload", columnDefinition = "jsonb")
    private String serverPayload;

    @Column(name = "client_payload", columnDefinition = "jsonb")
    private String clientPayload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
