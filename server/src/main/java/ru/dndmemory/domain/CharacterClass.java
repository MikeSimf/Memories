package ru.dndmemory.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/** Справочник классов персонажа: {@code code} — ключ, {@code name} — подпись. */
@Entity
@Table(name = "character_classes")
@Getter
@Setter
@NoArgsConstructor
public class CharacterClass {

    @Id
    @Column(length = 48)
    private String code;

    @Column(nullable = false, length = 96)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
