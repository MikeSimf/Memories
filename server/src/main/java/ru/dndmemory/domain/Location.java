package ru.dndmemory.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.Table;

/** Локация: своих полей нет, всё нужное уже есть в имени, описании и картинке. */
@Entity
@Table(name = "locations")
@Getter
@Setter
@NoArgsConstructor
public class Location extends ImageExtender {

    @Override
    public String matchKey() {
        return normalizePart(getName());
    }
}
