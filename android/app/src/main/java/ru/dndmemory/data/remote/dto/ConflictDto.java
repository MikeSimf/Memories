package ru.dndmemory.data.remote.dto;

import com.google.gson.JsonObject;

/** Состояние сервера при конфликте (docs/sync.md, раздел 3.1). */
public class ConflictDto {

    /** stale_base | forbidden */
    public String reason;
    public long serverRevision;
    /** Полное состояние серверного объекта. */
    public JsonObject server;
}
