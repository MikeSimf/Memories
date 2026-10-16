package ru.dndmemory.data.remote.dto;

/** Элемент результата поиска (docs/api.md, раздел 8). */
public class SearchItemDto {

    /** HERO | CHARACTER | LOCATION | ITEM | RETELLING */
    public String kind;
    public String id;
    public String title;
    /** Подсвеченный фрагмент текста, теги {@code <b>} уже расставлены сервером. */
    public String snippet;
    public long revision;
    public String updatedAt;
}
