package ru.dndmemory.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.ArrayList;
import java.util.List;

/**
 * Кампания в локальной базе. Поле {@code latestRevision} — точка отсчёта
 * синхронизации, {@code myPermissions} — плоский список прав от сервера.
 */
@Entity(tableName = "campaigns")
public class CampaignRoom {

    @PrimaryKey
    @NonNull
    public String id = "";

    public String title;
    public String description;

    /** OWNER | EDITOR_MASTER | EDITOR_HERO | VIEWER */
    public String myRole;

    /** Герой, привязанный к текущему участнику. */
    public String myLinkedHeroId;

    public List<String> myPermissions = new ArrayList<>();

    public int memberCount;
    public int entityCount;
    public int retellingCount;

    public long latestRevision;
    public String updatedAt;

    /** Локальный признак «скрыта у меня» — не уходит на сервер. */
    public boolean hidden;

    public CampaignRoom() {
    }

    public boolean hasPermission(String permission) {
        return myPermissions != null && myPermissions.contains(permission);
    }
}
