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
import java.util.UUID;

/** Предмет: у него есть владелец «как написано в мире» и необязательная ссылка на героя или персонажа. */
@Entity
@Table(name = "items")
@Getter
@Setter
@NoArgsConstructor
public class Item extends GameEntitySupport
        implements GameEntityExtender, NamedExtender, SyncExtended, ImageExtender {

    /** Владелец так, как его называет мастер: «гильдия воров», «Лира Ксенофонт». */
    @Column(name = "owner_name", length = 200)
    private String ownerName;

    @Column(name = "owner_hero_id")
    private UUID ownerHeroId;

    @Column(name = "owner_character_id")
    private UUID ownerCharacterId;

    @Override
    public String matchKey() {
        return normalizePart(getName());
    }
}
