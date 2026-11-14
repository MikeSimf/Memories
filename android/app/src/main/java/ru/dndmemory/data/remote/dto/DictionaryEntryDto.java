package ru.dndmemory.data.remote.dto;

/** Значение справочника кампании: разрешено ли и используется ли. */
public class DictionaryEntryDto {
    public String kind;
    public String name;
    public String iconFileId;
    public boolean enabled;
    public boolean used;
}
