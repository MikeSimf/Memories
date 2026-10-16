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
 * Приглашение в кампанию: ссылка и QR-код содержат {@code token}.
 * Приглашение строго одноразовое — {@code maxUses} всегда 1, принятие его гасит.
 */
@Entity
@Table(name = "invites")
@Getter
@Setter
@NoArgsConstructor
public class Invite {

    /** Приглашение одноразовое: второго использования не бывает. */
    public static final int MAX_USES = 1;

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(nullable = false, unique = true, length = 16)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role;

    /** Герой, к которому привяжется приглашённый (герой — отдельная таблица). */
    @Column(name = "linked_hero_id")
    private UUID linkedHeroId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "max_uses", nullable = false)
    private int maxUses = MAX_USES;

    @Column(nullable = false)
    private int uses;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public boolean isValid() {
        return !revoked && uses < maxUses && expiresAt.isAfter(Instant.now());
    }

    /** Погасить приглашение: использований больше не будет. */
    public void consume() {
        this.uses = this.maxUses;
    }
}
