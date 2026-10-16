package ru.dndmemory.data.remote.dto;

/**
 * Предмет (docs/api.md, раздел 6). {@code ownerName} — владелец «как написано
 * в мире»; {@code ownerHeroId} или {@code ownerCharacterId} — необязательная
 * ссылка, не более одной.
 */
public class ItemDto extends EntityDto {

    public String ownerName;
    public String ownerHeroId;
    public String ownerHeroName;
    public String ownerCharacterId;
    public String ownerCharacterName;
}
