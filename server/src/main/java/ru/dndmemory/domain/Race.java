package ru.dndmemory.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Справочник рас (docs/api.md, раздел 6): {@code code} — машинный ключ,
 * {@code name} — как показывать. Раса у героя и персонажа хранится строкой,
 * чтобы старые записи и свободный ввод продолжали работать.
 */
@Entity
@Table(name = "races")
@Getter
@Setter
@NoArgsConstructor
public class Race {

    @Id
    @Column(length = 48)
    private String code;

    @Column(nullable = false, length = 96)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
