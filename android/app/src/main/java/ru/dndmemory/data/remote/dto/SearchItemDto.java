package ru.dndmemory.data.remote.dto;

/** Элемент результата поиска (docs/api.md, раздел 8). */
public class SearchItemDto {

    /** ENTITY | RETELLING */
    public String kind;
    public String id;
    /** Тип объекта для ENTITY, иначе null. */
    public String type;
    public String title;
    public String snippet;
    public long revision;
    public String updatedAt;
}
