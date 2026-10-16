package ru.dndmemory.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.UUID;

/**
 * Герой партии — персонаж игрока. Создаёт, правит и удаляет героя только владелец
 * кампании (docs/redesign-v2.md, раздел 3, «Права»).
 */
@Entity
@Table(name = "heroes")
@Getter
@Setter
@NoArgsConstructor
public class Hero extends ImageExtender {

    /** Раса героя, как написано в мире. */
    @Column(length = 120)
    private String race;

    /** Класс героя (в коде — klass, потому что class зарезервирован языком). */
    @Column(length = 120)
    private String klass;

    /** Кто отыгрывает героя; ссылка на пользователя необязательна. */
    @Column(name = "player_user_id")
    private UUID playerUserId;

    @Override
    public String matchKey() {
        return normalizePart(getName());
    }
}
