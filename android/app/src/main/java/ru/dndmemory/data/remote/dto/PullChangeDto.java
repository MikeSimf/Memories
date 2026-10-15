package ru.dndmemory.data.remote.dto;

import com.google.gson.JsonObject;

/** Одна запись журнала изменений при pull. */
public class PullChangeDto {

    public long revision;
    /** ENTITY | RETELLING | CAMPAIGN | MEMBER */
    public String objectType;
    public String objectId;
    /** UPSERT | DELETE */
    public String op;
    public JsonObject payload;
    public String actorUserId;
    public String clientChangeId;
    public String createdAt;
}
