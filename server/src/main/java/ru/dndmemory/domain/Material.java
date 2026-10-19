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

/**
 * Фото-материал кампании: карта, портрет, письмо — картинка плюс название
 * и описание. Живёт в кампании, а не в одном из четырёх классов справочника,
 * но использует те же поля синхронизации и то же право на правку.
 */
@Entity
@Table(name = "materials")
@Getter
@Setter
@NoArgsConstructor
public class Material extends GameEntitySupport
        implements GameEntityExtender, NamedExtender, SyncExtended, ImageExtender {

    @Override
    public String matchKey() {
        return normalizePart(getName());
    }
}
