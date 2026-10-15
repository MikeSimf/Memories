package ru.dndmemory.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Черновик пересказа. Не попадает ни в {@code pending_changes}, ни в push:
 * пока автор не нажмёт «Опубликовать», текст остаётся на устройстве
 * (docs/sync.md, раздел 4).
 */
@Entity(
        tableName = "retelling_drafts",
        indices = {@Index(value = {"campaignId"})}
)
public class RetellingDraftRoom {

    /** id будущего объекта — генерируется клиентом сразу. */
    @PrimaryKey
    @NonNull
    public String id = "";

    public String campaignId;

    /** id уже опубликованной версии, если черновик — правка существующего пересказа. */
    public String remoteId;

    public String title;
    public String body;
    public String authorEntityId;
    public String sessionDate;
    public long baseRevision;
    public String updatedAt;

    public RetellingDraftRoom() {
    }

    /** Черновик правки существующего пересказа. */
    public boolean isEdit() {
        return remoteId != null && !remoteId.isEmpty();
    }
}
