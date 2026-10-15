package ru.dndmemory.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Состояние синхронизации кампании: одна строка на кампанию. */
@Entity(tableName = "sync_state")
public class SyncStateRoom {

    @PrimaryKey
    @NonNull
    public String campaignId = "";

    /** Последняя применённая ревизия журнала сервера. */
    public long lastPulledRevision;

    /** Последняя известная ревизия сервера. */
    public long latestRevision;

    public String lastSyncAt;
    public String lastError;

    public SyncStateRoom() {
    }
}
