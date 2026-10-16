package ru.dndmemory.data.remote.dto;

import com.google.gson.JsonObject;

/** Одна локальная правка в теле push (docs/sync.md, раздел 3.1). */
public class PushChangeDto {

    public String clientChangeId;
    /** HERO | CHARACTER | LOCATION | ITEM | RETELLING | CAMPAIGN | MEMBER */
    public String objectType;
    public String objectId;
    /** UPSERT | DELETE */
    public String op;
    public long baseRevision;
    /** Объект в форме DTO своего класса либо надгробие. */
    public JsonObject payload;
}
