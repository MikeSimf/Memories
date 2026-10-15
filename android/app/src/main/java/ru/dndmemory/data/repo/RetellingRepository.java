package ru.dndmemory.data.repo;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import java.util.ArrayList;
import java.util.List;

import ru.dndmemory.data.Mappers;
import ru.dndmemory.data.local.AppDatabase;
import ru.dndmemory.data.local.entity.RetellingDraftRoom;
import ru.dndmemory.data.local.entity.RetellingRoom;
import ru.dndmemory.data.model.ChangeOp;
import ru.dndmemory.data.remote.ApiCall;
import ru.dndmemory.data.remote.ApiException;
import ru.dndmemory.data.remote.ApiService;
import ru.dndmemory.data.remote.dto.RetellingDto;
import ru.dndmemory.util.AppExecutors;
import ru.dndmemory.util.DateFormats;
import ru.dndmemory.util.Ids;
import ru.dndmemory.util.RepoCallback;

/**
 * Пересказы. Черновик живёт только в {@code retelling_drafts} и уходит на сервер
 * лишь после нажатия «Опубликовать» — тогда он превращается в {@code PUBLISHED}
 * и попадает в {@code pending_changes} (docs/sync.md, раздел 4).
 */
public class RetellingRepository {

    private final AppDatabase db;
    private final ApiService api;
    private final PendingWriter writer;
    private final AppExecutors exec;

    public RetellingRepository(AppDatabase db, ApiService api, PendingWriter writer, AppExecutors exec) {
        this.db = db;
        this.api = api;
        this.writer = writer;
        this.exec = exec;
    }

    // --- Чтение -------------------------------------------------------------

    public LiveData<List<RetellingRoom>> observePublished(String campaignId) {
        return db.retellingDao().observeByCampaign(campaignId);
    }

    public LiveData<List<RetellingDraftRoom>> observeDrafts(String campaignId) {
        return db.retellingDraftDao().observeByCampaign(campaignId);
    }

    public LiveData<RetellingRoom> observe(String retellingId) {
        return db.retellingDao().observe(retellingId);
    }

    public LiveData<RetellingDraftRoom> observeDraft(String draftId) {
        return db.retellingDraftDao().observe(draftId);
    }

    @Nullable
    public RetellingRoom find(String retellingId) {
        return db.retellingDao().find(retellingId);
    }

    @Nullable
    public RetellingDraftRoom findDraft(String draftId) {
        return db.retellingDraftDao().find(draftId);
    }

    @Nullable
    public RetellingDraftRoom findDraftByRemoteId(String remoteId) {
        return db.retellingDraftDao().findByRemoteId(remoteId);
    }

    public List<RetellingRoom> findAllNow(String campaignId) {
        return db.retellingDao().findAllNow(campaignId);
    }

    public LiveData<List<RetellingRoom>> search(String campaignId, String query) {
        return db.retellingDao().search(campaignId, "%" + (query == null ? "" : query.trim()) + "%");
    }

    // --- Черновики ----------------------------------------------------------

    /** Новый черновик: id будущего пересказа известен сразу. */
    public RetellingDraftRoom createDraft(String campaignId, @Nullable String authorEntityId) {
        RetellingDraftRoom draft = new RetellingDraftRoom();
        draft.id = Ids.uuid();
        draft.campaignId = campaignId;
        draft.remoteId = null;
        draft.title = "";
        draft.body = "";
        draft.authorEntityId = authorEntityId;
        draft.sessionDate = DateFormats.todayDate();
        draft.baseRevision = 0L;
        draft.updatedAt = DateFormats.nowIso();
        return draft;
    }

    /** Черновик правки уже опубликованного пересказа. */
    public RetellingDraftRoom createDraftFor(RetellingRoom published) {
        RetellingDraftRoom draft = new RetellingDraftRoom();
        draft.id = Ids.uuid();
        draft.campaignId = published.campaignId;
        draft.remoteId = published.id;
        draft.title = published.title;
        draft.body = published.body;
        draft.authorEntityId = published.authorEntityId;
        draft.sessionDate = published.sessionDate;
        draft.baseRevision = published.revision;
        draft.updatedAt = DateFormats.nowIso();
        return draft;
    }

    public void saveDraft(RetellingDraftRoom draft, RepoCallback<RetellingDraftRoom> callback) {
        exec.io(() -> {
            draft.updatedAt = DateFormats.nowIso();
            db.retellingDraftDao().upsert(draft);
            return draft;
        }, callback);
    }

    public void deleteDraft(String draftId, RepoCallback<Void> callback) {
        exec.io(() -> {
            db.retellingDraftDao().delete(draftId);
            return null;
        }, callback);
    }

    /**
     * Публикация черновика: он становится обычным пересказом и ставится в очередь
     * на отправку. На сервер {@code DRAFT} не уходит никогда.
     */
    public void publish(RetellingDraftRoom draft, RepoCallback<RetellingRoom> callback) {
        exec.io(() -> {
            String now = DateFormats.nowIso();
            RetellingRoom room;
            if (draft.isEdit()) {
                room = db.retellingDao().find(draft.remoteId);
                if (room == null) {
                    throw ApiException.unexpected(
                            new IllegalStateException("Опубликованный пересказ не найден локально"));
                }
            } else {
                room = new RetellingRoom();
                room.id = draft.id;
                room.clientId = draft.id;
                room.campaignId = draft.campaignId;
                room.createdAt = now;
                room.revision = 0L;
            }
            room.title = draft.title;
            room.body = draft.body;
            room.authorEntityId = draft.authorEntityId;
            room.sessionDate = draft.sessionDate;
            room.status = "PUBLISHED";
            room.updatedAt = now;
            room.dirty = true;
            room.deleted = false;
            room.links = LinkResolver.resolve(db.entityDao(), room.campaignId,
                    room.title, room.body);
            db.retellingDao().upsert(room);
            writer.retelling(room, ChangeOp.UPSERT);
            db.retellingDraftDao().delete(draft.id);
            return room;
        }, callback);
    }

    // --- Опубликованные -----------------------------------------------------

    public void delete(String retellingId, RepoCallback<Void> callback) {
        exec.io(() -> {
            RetellingRoom room = db.retellingDao().find(retellingId);
            if (room == null) {
                return null;
            }
            room.updatedAt = DateFormats.nowIso();
            db.retellingDao().markDeleted(retellingId, room.updatedAt);
            room.deleted = true;
            room.dirty = true;
            writer.retelling(room, ChangeOp.DELETE);
            return null;
        }, callback);
    }

    /** «Оставить серверную версию» после расхождения. */
    public void acceptServerVersion(String retellingId, RepoCallback<RetellingRoom> callback) {
        exec.io(() -> {
            RetellingRoom local = db.retellingDao().find(retellingId);
            if (local == null) {
                throw ApiException.unexpected(
                        new IllegalStateException("Пересказ не найден локально"));
            }
            RetellingDto dto = ApiCall.execute(
                    api.retelling(local.campaignId, retellingId));
            RetellingRoom fresh = Mappers.toRoom(dto, local.campaignId);
            fresh.dirty = false;
            fresh.conflictJson = null;
            db.retellingDao().upsert(fresh);
            return fresh;
        }, callback);
    }

    /** «Перезаписать своей версией» после расхождения. */
    public void overwriteWithLocal(String retellingId, RepoCallback<RetellingRoom> callback) {
        exec.io(() -> {
            RetellingRoom room = db.retellingDao().find(retellingId);
            if (room == null) {
                throw ApiException.unexpected(
                        new IllegalStateException("Пересказ не найден локально"));
            }
            room.updatedAt = DateFormats.nowIso();
            room.conflictJson = null;
            room.dirty = true;
            room.deleted = false;
            db.retellingDao().upsert(room);
            writer.retelling(room, ChangeOp.UPSERT);
            return room;
        }, callback);
    }

    public void refresh(String campaignId, RepoCallback<List<RetellingRoom>> callback) {
        exec.io(() -> {
            List<RetellingDto> remote = ApiCall.execute(
                    api.retellings(campaignId, null, null, 500, null));
            List<RetellingRoom> rooms = new ArrayList<>();
            if (remote != null) {
                for (RetellingDto dto : remote) {
                    if (dto == null || dto.id == null) {
                        continue;
                    }
                    RetellingRoom local = db.retellingDao().find(dto.id);
                    if (local != null && local.dirty) {
                        continue;
                    }
                    RetellingRoom incoming = Mappers.toRoom(dto, campaignId);
                    db.retellingDao().upsert(incoming);
                    rooms.add(incoming);
                }
            }
            return rooms;
        }, callback);
    }
}
