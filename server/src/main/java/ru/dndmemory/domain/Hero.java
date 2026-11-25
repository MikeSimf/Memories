package ru.dndmemory.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import ru.dndmemory.domain.extender.GameEntityExtender;
import ru.dndmemory.domain.extender.ImageExtender;
import ru.dndmemory.domain.extender.NamedExtender;
import ru.dndmemory.domain.extender.SyncExtended;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.OrderColumn;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.ArrayList;
import java.util.List;
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
public class Hero extends GameEntitySupport
        implements GameEntityExtender, NamedExtender, SyncExtended, ImageExtender {

    /** Раса героя, как написано в мире. */
    @Column(length = 120)
    private String race;

    /** Класс героя (в коде — klass, потому что class зарезервирован языком). */
    @Column(length = 120)
    private String klass;

    /** TRUE — жив, FALSE — мёртв, NULL — неизвестно. */
    @Column(nullable = true)
    private Boolean alive;

    /** Пол: female, male или unknown. */
    @Column(length = 16, nullable = false)
    private String gender = "unknown";

    /** Кто отыгрывает героя; ссылка на пользователя необязательна. */
    @Column(name = "player_user_id")
    private UUID playerUserId;

    private int strength = 10;
    private int dexterity = 10;
    private int constitution = 10;
    private int intelligence = 10;
    private int wisdom = 10;
    private int charisma = 10;

    private Integer passivePerception;
    private Integer initiative;
    private Integer speed;
    private Integer armorClass;
    private Integer maxHp;
    private Integer inspiration;

    /** Звания героя: «убийца волков», «вождь грунгов» и т. п. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "hero_titles", joinColumns = @JoinColumn(name = "hero_id"))
    @OrderColumn(name = "position")
    @Column(name = "title", length = 200)
    private List<String> titles = new ArrayList<>();

    @Override
    public String matchKey() {
        return normalizePart(getName());
    }
}
