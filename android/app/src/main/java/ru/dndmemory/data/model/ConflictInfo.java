package ru.dndmemory.data.model;

import androidx.annotation.Nullable;

/**
 * Расхождение с сервером: приходит в {@code conflict} ответа push
 * (docs/sync.md, раздел 3.1), хранится в объекте как {@code conflict_json}.
 */
public class ConflictInfo {

    /** {@code stale_base} | {@code forbidden} | иные причины от сервера. */
    public String reason;

    public long serverRevision;

    /** Полное состояние серверного объекта в JSON, либо {@code null}. */
    public String serverJson;

    public ConflictInfo() {
    }

    public ConflictInfo(@Nullable String reason, long serverRevision, @Nullable String serverJson) {
        this.reason = reason;
        this.serverRevision = serverRevision;
        this.serverJson = serverJson;
    }

    public boolean isForbidden() {
        return "forbidden".equalsIgnoreCase(reason);
    }
}
