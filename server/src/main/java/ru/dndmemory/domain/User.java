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
 * Пользователь. Вход — только по логину и паролю: внешних провайдеров больше нет
 * (docs/redesign-v2.md, раздел 1).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Короткое имя для входа: 3–32 символа [a-z0-9_.-], хранится в нижнем регистре. */
    @Column(nullable = false, unique = true, length = 32)
    private String login;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    /** Подтверждение почты появится вместе с SMTP; пока всегда false. */
    /** Служебный суперпользователь: видит все кампании, в участниках не числится. */
    @Column(nullable = false)
    private boolean superuser;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "password_hash", nullable = false, length = 120)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "avatar_file_id")
    private UUID avatarFileId;

    /** Телефон в формате +7XXXXXXXXXX. Необязателен и уникален; пароль на него не сбрасывается. */
    @Column(length = 32)
    private String phone;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isEmpty();
    }
}
