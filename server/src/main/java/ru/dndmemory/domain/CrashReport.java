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

/** Падение мобильного приложения, присланное с устройства. */
@Entity
@Table(name = "crash_reports")
@Getter
@Setter
@NoArgsConstructor
public class CrashReport {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "app_version", length = 40)
    private String appVersion;

    @Column(length = 200)
    private String device;

    @Column(nullable = false, columnDefinition = "text")
    private String stacktrace;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
