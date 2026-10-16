package ru.dndmemory.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import ru.dndmemory.domain.extender.GameEntityExtender;
import ru.dndmemory.domain.extender.ImageExtender;
import ru.dndmemory.domain.extender.NamedExtender;
import ru.dndmemory.domain.extender.SyncExtended;
import javax.persistence.Entity;
import javax.persistence.Table;

/** Локация: своих полей нет, всё нужное уже есть в имени, описании и картинке. */
@Entity
@Table(name = "locations")
@Getter
@Setter
@NoArgsConstructor
public class Location extends GameEntitySupport
        implements GameEntityExtender, NamedExtender, SyncExtended, ImageExtender {

    @Override
    public String matchKey() {
        return normalizePart(getName());
    }
}
