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

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    /** null у пользователей, вошедших только через Яндекс или VK. */
    @Column(name = "password_hash", length = 120)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "avatar_file_id")
    private UUID avatarFileId;

    /** Телефон в формате +7XXXXXXXXXX. Уникален; нужен для входа в веб-версию. */
    @Column(length = 32)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider = AuthProvider.LOCAL;

    @Column(name = "provider_user_id", length = 190)
    private String providerUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isEmpty();
    }
}
