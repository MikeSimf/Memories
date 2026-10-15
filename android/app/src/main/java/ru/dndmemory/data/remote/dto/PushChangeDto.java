package ru.dndmemory.data.remote.dto;

import com.google.gson.JsonObject;

/** Одна локальная правка в теле push. */
public class PushChangeDto {

    public String clientChangeId;
    /** ENTITY | RETELLING | CAMPAIGN | MEMBER */
    public String objectType;
    public String objectId;
    /** UPSERT | DELETE */
    public String op;
    public long baseRevision;
    public JsonObject payload;
}
