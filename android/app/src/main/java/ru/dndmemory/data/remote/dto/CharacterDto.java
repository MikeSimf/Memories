package ru.dndmemory.data.remote.dto;

/**
 * Персонаж (НПС, docs/api.md, раздел 6). Поиск по нему идёт ещё и по расе
 * с кратким описанием.
 */
public class CharacterDto extends EntityDto {

    public String race;
    public String shortDescription;
}
