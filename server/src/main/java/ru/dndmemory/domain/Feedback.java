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

/** Сообщение обратной связи от авторизованного пользователя. */
@Entity
@Table(name = "feedback")
@Getter
@Setter
@NoArgsConstructor
public class Feedback {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Column(length = 200)
    private String page;

    @Column(nullable = false, length = 16)
    private String status = "NEW";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
