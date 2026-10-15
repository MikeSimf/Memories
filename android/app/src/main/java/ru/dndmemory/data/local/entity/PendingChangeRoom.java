package ru.dndmemory.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Очередь неотправленных правок. {@code clientChangeId} — ключ идемпотентности,
 * {@code seq} задаёт порядок возникновения (push идёт строго по нему).
 */
@Entity(
        tableName = "pending_changes",
        indices = {
                @Index(value = {"clientChangeId"}, unique = true),
                @Index(value = {"campaignId"}),
                @Index(value = {"campaignId", "objectId"})
        }
)
public class PendingChangeRoom {

    @PrimaryKey(autoGenerate = true)
    public long seq;

    @NonNull
    public String clientChangeId = "";

    public String campaignId;

    /** ENTITY | RETELLING | CAMPAIGN | MEMBER */
    public String objectType;

    public String objectId;

    /** UPSERT | DELETE */
    public String op;

    public long baseRevision;

    /** Полное состояние объекта после правки, JSON. */
    public String payloadJson;

    public String createdAt;
    public int attempts;

    public PendingChangeRoom() {
    }
}
