package ru.dndmemory.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Устройство (телефон), получившее доступ по QR-коду администратора. */
@Entity
@Table(name = "devices")
@Getter
@Setter
@NoArgsConstructor
public class Device {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Кампания, для которой выдавали QR (устройство может участвовать в нескольких). */
    @Column(name = "campaign_id")
    private UUID campaignId;

    @Column(length = 120)
    private String label;

    /** android, web — чтобы админ видел, откуда вход. */
    @Column(length = 40)
    private String platform;

    @Column(name = "invite_id")
    private UUID inviteId;

    @Column(name = "enrolled_by")
    private UUID enrolledBy;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;
}
