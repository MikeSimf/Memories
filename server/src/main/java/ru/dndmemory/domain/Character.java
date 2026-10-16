package ru.dndmemory.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import ru.dndmemory.domain.extender.GameEntityExtender;
import ru.dndmemory.domain.extender.ImageExtender;
import ru.dndmemory.domain.extender.NamedExtender;
import ru.dndmemory.domain.extender.SyncExtended;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * Персонаж (НПС, монстр, союзник мастера). Сопоставляется по имени и расе: у одного
 * мастера легко живут два «Стражника» разных народов.
 */
@Entity
@Table(name = "characters")
@Getter
@Setter
@NoArgsConstructor
public class Character extends GameEntitySupport
        implements GameEntityExtender, NamedExtender, SyncExtended, ImageExtender {

    @Column(length = 120)
    private String race;

    /** Короткое описание — по нему тоже идёт поиск. */
    @Column(name = "short_description", columnDefinition = "text")
    private String shortDescription;

    @Override
    public String matchKey() {
        return normalizePart(getName()) + "|" + normalizePart(getRace());
    }

    @Override
    public String matchReason() {
        return "matched_by_name_and_race";
    }
}
