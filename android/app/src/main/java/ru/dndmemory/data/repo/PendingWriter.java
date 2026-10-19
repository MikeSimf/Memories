package ru.dndmemory.data.repo;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import ru.dndmemory.data.Mappers;
import ru.dndmemory.data.local.AppDatabase;
import ru.dndmemory.data.local.entity.CampaignRoom;
import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.data.local.entity.MaterialRoom;
import ru.dndmemory.data.local.entity.MemberRoom;
import ru.dndmemory.data.local.entity.PendingChangeRoom;
import ru.dndmemory.data.local.entity.RetellingRoom;
import ru.dndmemory.data.model.ChangeOp;
import ru.dndmemory.data.model.EntityType;
import ru.dndmemory.data.model.ObjectType;
import ru.dndmemory.data.remote.ApiClient;
import ru.dndmemory.util.DateFormats;
import ru.dndmemory.util.Ids;

/**
 * Кладёт локальную правку в очередь {@code pending_changes}. Дальше её забирает
 * {@link ru.dndmemory.sync.SyncEngine} и отправляет через push
 * (docs/sync.md, раздел 3.1).
 */
public class PendingWriter {

    private final AppDatabase db;
    private final Gson gson = ApiClient.gson();

    public PendingWriter(AppDatabase db) {
        this.db = db;
    }

    /** Класс объекта берётся из самой записи: {@code HERO}, {@code CHARACTER}, … */
    public void entity(EntityRoom room, ChangeOp op) {
        EntityType kind = EntityType.fromOrCharacter(room.kind);
        ObjectType type = ObjectType.from(kind);
        PendingChangeRoom change = base(room.campaignId, type, room.id, op, room.revision);
        if (op == ChangeOp.DELETE) {
            change.payloadJson = tombstone(room.id, type, room.updatedAt, room.revision);
        } else {
            change.payloadJson = gson.toJson(Mappers.toDto(room));
        }
        db.pendingChangeDao().insert(change);
    }

    public void retelling(RetellingRoom room, ChangeOp op) {
        PendingChangeRoom change = base(room.campaignId, ObjectType.RETELLING, room.id, op, room.revision);
        if (op == ChangeOp.DELETE) {
            change.payloadJson = tombstone(room.id, ObjectType.RETELLING, room.updatedAt, room.revision);
        } else {
            change.payloadJson = gson.toJson(Mappers.toDto(room));
        }
        db.pendingChangeDao().insert(change);
    }

    public void material(MaterialRoom room, ChangeOp op) {
        PendingChangeRoom change = base(room.campaignId, ObjectType.MATERIAL, room.id, op, room.revision);
        if (op == ChangeOp.DELETE) {
            change.payloadJson = tombstone(room.id, ObjectType.MATERIAL, room.updatedAt, room.revision);
        } else {
            change.payloadJson = gson.toJson(Mappers.toDto(room));
        }
        db.pendingChangeDao().insert(change);
    }

    public void campaign(CampaignRoom room, ChangeOp op) {
        PendingChangeRoom change = base(room.id, ObjectType.CAMPAIGN, room.id, op, room.latestRevision);
        if (op == ChangeOp.DELETE) {
            change.payloadJson = tombstone(room.id, ObjectType.CAMPAIGN, room.updatedAt, 0L);
        } else {
            change.payloadJson = gson.toJson(Mappers.toDto(room));
        }
        db.pendingChangeDao().insert(change);
    }

    public void member(MemberRoom room, ChangeOp op) {
        PendingChangeRoom change = base(room.campaignId, ObjectType.MEMBER, room.id, op, 0L);
        if (op == ChangeOp.DELETE) {
            change.payloadJson = tombstone(room.id, ObjectType.MEMBER, DateFormats.nowIso(), 0L);
        } else {
            change.payloadJson = gson.toJson(Mappers.toDto(room));
        }
        db.pendingChangeDao().insert(change);
    }

    /** Надгробие для DELETE (docs/sync.md, раздел 1). */
    public static String tombstone(String objectId, ObjectType type, String updatedAt, long revision) {
        JsonObject json = new JsonObject();
        json.addProperty("id", objectId);
        if (type != null) {
            json.addProperty("objectType", type.name());
        }
        json.addProperty("deleted", true);
        json.addProperty("revision", revision);
        json.addProperty("updatedAt", updatedAt == null ? DateFormats.nowIso() : updatedAt);
        return json.toString();
    }

    private PendingChangeRoom base(String campaignId, ObjectType type, String objectId,
                                   ChangeOp op, long baseRevision) {
        PendingChangeRoom change = new PendingChangeRoom();
        change.clientChangeId = Ids.changeId();
        change.campaignId = campaignId;
        change.objectType = type == null ? ObjectType.CHARACTER.name() : type.name();
        change.objectId = objectId;
        change.op = op == null ? ChangeOp.UPSERT.name() : op.name();
        change.baseRevision = baseRevision;
        change.createdAt = DateFormats.nowIso();
        change.attempts = 0;
        return change;
    }
}
