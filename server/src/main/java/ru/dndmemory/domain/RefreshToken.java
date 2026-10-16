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

/**
 * Refresh-токен хранится в виде SHA-256 хеша, сам токен нигде не сохраняется.
 * {@code platform} отличает приложение от веба: у приложения сессия живёт год.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    /** android или web: по нему выбирается срок жизни новой сессии. */
    @Column(length = 20)
    private String platform;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
