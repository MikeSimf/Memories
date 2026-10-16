package ru.dndmemory.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Участник кампании с ролью и необязательной привязкой к герою. */
@Entity(
        tableName = "members",
        indices = {
                @Index(value = {"campaignId"}),
                @Index(value = {"campaignId", "userId"})
        }
)
public class MemberRoom {

    @PrimaryKey
    @NonNull
    public String id = "";

    public String campaignId;
    public String userId;
    public String displayName;
    public String email;

    /** OWNER | EDITOR_MASTER | EDITOR_HERO | VIEWER */
    public String role;

    /** Герой, за которого играет участник (docs/api.md, раздел 5). */
    public String linkedHeroId;
    public String linkedHeroName;

    /** INVITED | ACTIVE | REMOVED */
    public String status;

    public String createdAt;

    public MemberRoom() {
    }
}
