package ru.dndmemory.data.remote.dto;

import java.util.ArrayList;
import java.util.List;

import ru.dndmemory.data.model.HeroItem;

/**
 * Герой (docs/api.md, раздел 6). Создавать, править и удалять может только
 * владелец кампании — право {@code hero.write}.
 */
public class HeroDto extends EntityDto {

    public String race;
    public String klass;

    /** Кто отыгрывает героя (ссылка на пользователя, необязательна). */
    public String playerUserId;
    public String playerName;

    /** Котомка: переданный список заменяет прежний целиком. */
    public List<HeroItem> items = new ArrayList<>();
}
