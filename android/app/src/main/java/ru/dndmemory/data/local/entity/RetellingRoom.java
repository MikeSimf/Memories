package ru.dndmemory.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.ArrayList;
import java.util.List;

import ru.dndmemory.data.model.EntityLink;

/** Опубликованный пересказ сессии. Черновики живут в {@link RetellingDraftRoom}. */
@Entity(
        tableName = "retellings",
        indices = {
                @Index(value = {"campaignId"}),
                @Index(value = {"campaignId", "sessionDate"})
        }
)
public class RetellingRoom {

    @PrimaryKey
    @NonNull
    public String id = "";

    public String campaignId;
    public String clientId;

    public String title;
    public String body;

    public String authorEntityId;
    public String authorEntityName;

    /** ISO-8601, дата в формате yyyy-MM-dd. */
    public String sessionDate;

    public List<EntityLink> links = new ArrayList<>();

    /** Всегда PUBLISHED: черновики сюда не попадают. */
    public String status;

    public String createdBy;
    public String updatedBy;
    public long revision;
    public String createdAt;
    public String updatedAt;

    public boolean deleted;
    public boolean dirty;
    public String conflictJson;

    public RetellingRoom() {
    }

    public boolean hasConflict() {
        return conflictJson != null && !conflictJson.isEmpty();
    }
}
