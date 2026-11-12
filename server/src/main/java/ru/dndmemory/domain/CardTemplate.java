package ru.dndmemory.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Шаблон карточки списка. */
@Entity
@Table(name = "card_templates")
@Getter
@Setter
@NoArgsConstructor
public class CardTemplate {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "object_type", nullable = false, length = 24)
    private String objectType;

    @Column(nullable = false, length = 16)
    private String kind = "LIST";

    @Column(nullable = false, columnDefinition = "text")
    private String formula = "true";

    @Column(nullable = false)
    private int priority;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<CardBlock> blocks = new ArrayList<>();
}
