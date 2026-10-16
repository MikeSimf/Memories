package ru.dndmemory.sync;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import ru.dndmemory.data.Mappers;
import ru.dndmemory.data.local.AppDatabase;
import ru.dndmemory.data.local.entity.CampaignRoom;
import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.data.local.entity.MemberRoom;
import ru.dndmemory.data.local.entity.PendingChangeRoom;
import ru.dndmemory.data.local.entity.RetellingRoom;
import ru.dndmemory.data.local.entity.SyncStateRoom;
import ru.dndmemory.data.model.ConflictInfo;
import ru.dndmemory.data.model.EntityType;
import ru.dndmemory.data.model.ObjectType;
import ru.dndmemory.data.model.SyncResult;
import ru.dndmemory.data.model.SyncStatus;
import ru.dndmemory.data.remote.ApiCall;
import ru.dndmemory.data.remote.ApiClient;
import ru.dndmemory.data.remote.ApiException;
import ru.dndmemory.data.remote.ApiService;
import ru.dndmemory.data.remote.dto.CampaignDto;
import ru.dndmemory.data.remote.dto.ConflictDto;
import ru.dndmemory.data.remote.dto.EntityDto;
import ru.dndmemory.data.remote.dto.MemberDto;
import ru.dndmemory.data.remote.dto.PullChangeDto;
import ru.dndmemory.data.remote.dto.PullResponse;
import ru.dndmemory.data.remote.dto.PushChangeDto;
import ru.dndmemory.data.remote.dto.PushRequest;
import ru.dndmemory.data.remote.dto.PushResponse;
import ru.dndmemory.data.remote.dto.PushResultDto;
import ru.dndmemory.data.remote.dto.RetellingDto;
import ru.dndmemory.data.remote.dto.SnapshotResponse;
import ru.dndmemory.util.DateFormats;

/**
 * Офлайн-первый протокол обмена (docs/sync.md).
 *
 * <p>Цикл строго такой: <b>сначала push, потом pull</b> — локальные правки
 * получают серверные ревизии, и следующий pull не возвращает их обратно.
 * Если журнал сервера скомпактился и пришёл {@code 410 resync_required},
 * выполняется полная перезагрузка через snapshot с курсорами.
 *
 * <p>В журнале больше нет общего {@code ENTITY}: у каждого класса объектов своё
 * значение {@code objectType}, а {@code payload} — форма DTO своего класса
 * (docs/api.md, раздел 9).
 */
public class SyncEngine {

    private static final int PULL_LIMIT = 500;
    private static final int SNAPSHOT_LIMIT = 500;

    private final AppDatabase db;
    private final ApiService api;
    private final String deviceClientId;
    private final Gson gson = ApiClient.gson();

    private final Object syncLock = new Object();
    private final MutableLiveData<SyncStatus> status = new MutableLiveData<>(SyncStatus.IDLE);
    private final MutableLiveData<SyncResult> lastResult = new MutableLiveData<>();
    private final CopyOnWriteArrayList<String> conflictObjects = new CopyOnWriteArrayList<>();

    public SyncEngine(AppDatabase db, ApiService api, String deviceClientId) {
        this.db = db;
        this.api = api;
        this.deviceClientId = deviceClientId;
    }

    public LiveData<SyncStatus> observeStatus() {
        return status;
    }

    public LiveData<SyncResult> observeLastResult() {
        return lastResult;
    }

    /** Объекты с расхождением — интерфейс показывает «требует внимания». */
    public List<String> conflictObjectIds() {
        return new ArrayList<>(conflictObjects);
    }

    /** Синхронизация всех кампаний: используется периодическим воркером. */
    public void syncAll() {
        for (CampaignRoom campaign : db.campaignDao().findAllNow()) {
            sync(campaign.id);
        }
    }

    /**
     * Полный цикл для одной кампании. Выполняется синхронно на фоновом потоке
     * (WorkManager или пул репозиториев), поэтому его нельзя звать из UI.
     */
    public SyncResult sync(String campaignId) {
        synchronized (syncLock) {
            if (campaignId == null || campaignId.isEmpty()) {
                return SyncResult.failure("Не указана кампания");
            }
            status.postValue(SyncStatus.SYNCING);
            try {
                ensureState(campaignId);
                SyncResult result = push(campaignId);
                if (result.resyncRequired) {
                    result = snapshot(campaignId);
                }
                if (!result.success && !result.resyncRequired) {
                    publishFailure(campaignId, result.errorMessage, false);
                    return result;
                }
                SyncResult pulled = pull(campaignId);
                if (pulled.resyncRequired) {
                    pulled = snapshot(campaignId);
                }
                if (!pulled.success && !pulled.resyncRequired) {
                    publishFailure(campaignId, pulled.errorMessage, false);
                    return pulled;
                }
                SyncResult merged = new SyncResult();
                merged.success = true;
                merged.pushed = result.pushed;
                merged.applied = result.applied;
                merged.conflictResolved = result.conflictResolved;
                merged.rejected = result.rejected;
                merged.pulled = pulled.pulled;
                merged.latestRevision = pulled.latestRevision;

                String now = DateFormats.nowIso();
                db.syncStateDao().markSynced(campaignId, now);
                status.postValue(merged.rejected > 0 ? SyncStatus.CONFLICT : SyncStatus.IDLE);
                lastResult.postValue(merged);
                return merged;
            } catch (ApiException e) {
                publishFailure(campaignId, describe(e), e.isNetwork());
                return SyncResult.failure(describe(e));
            }
        }
    }

    /** Ручная полная перезагрузка кампании (кнопка «Сбросить и загрузить заново»). */
    public SyncResult forceSnapshot(String campaignId) {
        synchronized (syncLock) {
            status.postValue(SyncStatus.SYNCING);
            try {
                SyncResult first = push(campaignId);
                if (!first.success && !first.resyncRequired) {
                    publishFailure(campaignId, first.errorMessage, false);
                    return first;
                }
                SyncResult result = snapshot(campaignId);
                status.postValue(result.success ? SyncStatus.IDLE : SyncStatus.ERROR);
                lastResult.postValue(result);
                return result;
            } catch (ApiException e) {
                publishFailure(campaignId, describe(e), e.isNetwork());
                return SyncResult.failure(describe(e));
            }
        }
    }

    // --- Push ---------------------------------------------------------------

    private SyncResult push(String campaignId) throws ApiException {
        List<PendingChangeRoom> pending = db.pendingChangeDao().list(campaignId);
        if (pending.isEmpty()) {
            return SyncResult.ok(0, 0, 0, currentRevision(campaignId));
        }
        PushRequest request = new PushRequest(deviceClientId);
        Map<String, String> localIds = new HashMap<>();
        for (PendingChangeRoom change : pending) {
            PushChangeDto dto = new PushChangeDto();
            dto.clientChangeId = change.clientChangeId;
            dto.objectType = change.objectType;
            dto.objectId = change.objectId;
            dto.op = change.op;
            dto.baseRevision = change.baseRevision;
            dto.payload = parsePayload(change.payloadJson);
            request.changes.add(dto);
            localIds.put(change.clientChangeId, change.objectId);
        }

        PushResponse response;
        try {
            response = ApiCall.execute(api.pushChanges(campaignId, request));
        } catch (ApiException e) {
            if (e.isResyncRequired()) {
                return SyncResult.resync();
            }
            db.pendingChangeDao().bumpAttempts(pending.get(0).clientChangeId);
            throw e;
        }
        if (response == null) {
            return SyncResult.failure("Пустой ответ push");
        }

        int applied = 0;
        int resolved = 0;
        int rejected = 0;
        for (PushResultDto result : response.results) {
            if (result == null || result.clientChangeId == null) {
                continue;
            }
            String localId = localIds.get(result.clientChangeId);
            if (result.isApplied() || result.isConflictResolved()) {
                db.pendingChangeDao().deleteById(result.clientChangeId);
                String serverId = adoptServerId(localId, result.objectId);
                markAccepted(campaignId, serverId, result.revision);
                if (localId != null) {
                    conflictObjects.remove(localId);
                }
                conflictObjects.remove(result.objectId);
                if (result.isApplied()) {
                    applied++;
                } else {
                    resolved++;
                }
            } else if (result.isRejected()) {
                db.pendingChangeDao().deleteById(result.clientChangeId);
                String objectId = localId != null ? localId : result.objectId;
                markRejected(campaignId, objectId, result.conflict);
                if (objectId != null && !conflictObjects.contains(objectId)) {
                    conflictObjects.add(objectId);
                }
                rejected++;
            }
        }
        db.syncStateDao().setLatestRevision(campaignId, response.latestRevision);

        SyncResult ok = SyncResult.ok(applied, resolved, 0, response.latestRevision);
        ok.rejected = rejected;
        ok.pushed = applied + resolved + rejected;
        if (rejected > 0) {
            status.postValue(SyncStatus.CONFLICT);
        }
        return ok;
    }

    /**
     * Сервер мог найти объект по ключу сопоставления и вернуть свой id
     * (docs/sync.md, раздел 4): локальную запись переименовываем, чтобы правки
     * не удвоились. Дубль, который уже есть локально, просто убираем.
     */
    @Nullable
    private String adoptServerId(@Nullable String localId, @Nullable String serverId) {
        if (localId == null || serverId == null || serverId.isEmpty() || serverId.equals(localId)) {
            return serverId == null || serverId.isEmpty() ? localId : serverId;
        }
        EntityRoom existing = db.entityDao().find(serverId);
        if (existing != null) {
            db.entityDao().delete(localId);
        } else {
            db.entityDao().reassignId(localId, serverId);
        }
        return serverId;
    }

    private void markAccepted(String campaignId, String objectId, long revision) {
        if (objectId == null) {
            return;
        }
        boolean forward = revision > currentRevision(campaignId);
        EntityRoom entity = db.entityDao().find(objectId);
        if (entity != null) {
            db.entityDao().markClean(objectId, revision);
            if (forward) {
                db.campaignDao().updateRevision(campaignId, revision);
            }
            return;
        }
        RetellingRoom retelling = db.retellingDao().find(objectId);
        if (retelling != null) {
            db.retellingDao().markClean(objectId, revision);
            if (forward) {
                db.campaignDao().updateRevision(campaignId, revision);
            }
            return;
        }
        if (forward) {
            db.campaignDao().updateRevision(campaignId, revision);
        }
    }

    private void markRejected(String campaignId, String objectId, ConflictDto conflict) {
        if (objectId == null) {
            return;
        }
        ConflictInfo info = new ConflictInfo(
                conflict == null ? null : conflict.reason,
                conflict == null ? 0L : conflict.serverRevision,
                conflict == null || conflict.server == null ? null : conflict.server.toString());
        String json = gson.toJson(info);

        EntityRoom entity = db.entityDao().find(objectId);
        if (entity != null) {
            db.entityDao().setConflict(objectId, json);
            return;
        }
        RetellingRoom retelling = db.retellingDao().find(objectId);
        if (retelling != null) {
            db.retellingDao().setConflict(objectId, json);
        }
    }

    // --- Pull ---------------------------------------------------------------

    private SyncResult pull(String campaignId) throws ApiException {
        SyncStateRoom state = ensureState(campaignId);
        long since = state.lastPulledRevision;
        int applied = 0;
        long latest = since;
        int guard = 0;

        while (guard++ < 200) {
            PullResponse page;
            try {
                page = ApiCall.execute(api.pullChanges(campaignId, since, PULL_LIMIT, deviceClientId));
            } catch (ApiException e) {
                if (e.isResyncRequired()) {
                    return SyncResult.resync();
                }
                throw e;
            }
            if (page == null) {
                break;
            }
            for (PullChangeDto change : page.changes) {
                if (change == null) {
                    continue;
                }
                applyChange(campaignId, change);
                applied++;
                since = Math.max(since, change.revision);
            }
            if (page.latestRevision > 0) {
                latest = page.latestRevision;
            }
            if (!page.hasMore || page.changes.isEmpty()) {
                break;
            }
        }

        // lastPulledRevision = latestRevision: шаг 5 цикла синхронизации.
        if (latest > 0) {
            db.syncStateDao().setPulledRevision(campaignId, latest);
        }
        db.campaignDao().updateRevision(campaignId, latest);
        return SyncResult.ok(0, 0, applied, latest);
    }

    /** Применение одной записи журнала к локальной базе. */
    private void applyChange(String campaignId, PullChangeDto change) {
        ObjectType type = ObjectType.from(change.objectType);
        JsonObject payload = change.payload;
        boolean deleted = "DELETE".equalsIgnoreCase(change.op)
                || (payload != null && payload.has("deleted")
                && !payload.get("deleted").isJsonNull() && payload.get("deleted").getAsBoolean());

        if (type == null) {
            return;
        }
        switch (type) {
            case HERO:
            case CHARACTER:
            case LOCATION:
            case ITEM: {
                applyEntityChange(campaignId, change, type.entityType(), deleted, payload);
                break;
            }
            case RETELLING: {
                RetellingRoom local = db.retellingDao().find(change.objectId);
                if (local != null && local.dirty) {
                    return;
                }
                if (deleted) {
                    if (local != null) {
                        db.retellingDao().applyTombstone(change.objectId, change.revision,
                                updatedAt(payload, change.createdAt));
                    }
                    return;
                }
                if (payload == null) {
                    return;
                }
                RetellingDto dto = gson.fromJson(payload, RetellingDto.class);
                if (dto != null) {
                    // DRAFT на сервер не попадает, но подстрахуемся.
                    if (dto.status == null || !"DRAFT".equalsIgnoreCase(dto.status)) {
                        db.retellingDao().upsert(Mappers.toRoom(dto, campaignId));
                    }
                }
                break;
            }
            case MEMBER: {
                if (deleted) {
                    db.memberDao().delete(change.objectId);
                    return;
                }
                if (payload == null) {
                    return;
                }
                MemberDto dto = gson.fromJson(payload, MemberDto.class);
                if (dto != null) {
                    db.memberDao().upsert(Mappers.toRoom(dto, campaignId));
                }
                break;
            }
            case CAMPAIGN: {
                if (payload == null) {
                    return;
                }
                CampaignDto dto = gson.fromJson(payload, CampaignDto.class);
                if (dto == null) {
                    return;
                }
                CampaignRoom existing = db.campaignDao().find(campaignId);
                CampaignRoom incoming = Mappers.toRoom(dto);
                if (incoming.id == null || incoming.id.isEmpty()) {
                    incoming.id = campaignId;
                }
                if (existing != null) {
                    incoming.latestRevision = Math.max(existing.latestRevision, incoming.latestRevision);
                    if (incoming.myPermissions == null || incoming.myPermissions.isEmpty()) {
                        incoming.myPermissions = existing.myPermissions;
                    }
                    if (incoming.myRole == null) {
                        incoming.myRole = existing.myRole;
                    }
                }
                db.campaignDao().upsert(incoming);
                break;
            }
            default:
                break;
        }
    }

    /**
     * Правка объекта кампании: тело в форме DTO своего класса
     * ({@code HERO}, {@code CHARACTER}, {@code LOCATION}, {@code ITEM}).
     */
    private void applyEntityChange(String campaignId, PullChangeDto change,
                                   @Nullable EntityType kind, boolean deleted,
                                   @Nullable JsonObject payload) {
        if (kind == null) {
            return;
        }
        EntityRoom local = db.entityDao().find(change.objectId);
        if (local != null && local.dirty) {
            return; // не затираем неотправленную локальную правку
        }
        if (deleted) {
            if (local != null) {
                db.entityDao().applyTombstone(change.objectId, change.revision,
                        updatedAt(payload, change.createdAt));
            }
            return;
        }
        if (payload == null) {
            return;
        }
        EntityDto dto = gson.fromJson(payload, Mappers.dtoClass(kind));
        if (dto != null) {
            EntityRoom incoming = Mappers.toRoom(dto, kind, campaignId);
            if (incoming.id == null || incoming.id.isEmpty()) {
                incoming.id = change.objectId;
            }
            db.entityDao().upsert(incoming);
        }
    }

    // --- Snapshot (первый вход, смена устройства, resync) -------------------

    private SyncResult snapshot(String campaignId) throws ApiException {
        List<EntityRoom> entities = new ArrayList<>();
        List<RetellingRoom> retellings = new ArrayList<>();
        List<MemberRoom> members = new ArrayList<>();
        CampaignRoom campaign = null;
        long latest = 0L;
        String cursor = null;
        int guard = 0;

        do {
            SnapshotResponse page = ApiCall.execute(
                    api.snapshot(campaignId, SNAPSHOT_LIMIT, cursor));
            if (page == null) {
                break;
            }
            // За одну страницу сервер отдаёт один класс: heroes, characters,
            // locations, items, retellings (docs/sync.md, раздел 3.3).
            addEntities(entities, page.heroes, EntityType.HERO, campaignId);
            addEntities(entities, page.characters, EntityType.CHARACTER, campaignId);
            addEntities(entities, page.locations, EntityType.LOCATION, campaignId);
            addEntities(entities, page.items, EntityType.ITEM, campaignId);
            if (page.retellings != null) {
                for (RetellingDto dto : page.retellings) {
                    if (dto != null && dto.id != null) {
                        retellings.add(Mappers.toRoom(dto, campaignId));
                    }
                }
            }
            if (page.members != null) {
                for (MemberDto dto : page.members) {
                    if (dto != null && dto.id != null) {
                        members.add(Mappers.toRoom(dto, campaignId));
                    }
                }
            }
            if (page.campaign != null) {
                campaign = Mappers.toRoom(page.campaign);
                if (campaign.id == null || campaign.id.isEmpty()) {
                    campaign.id = campaignId;
                }
            }
            if (page.latestRevision > 0) {
                latest = page.latestRevision;
            }
            cursor = page.nextCursor;
        } while (cursor != null && !cursor.isEmpty() && guard++ < 200);

        // Локальные черновики пересказов не трогаем: они живут только на устройстве.
        db.entityDao().clearForCampaign(campaignId);
        db.entityDao().upsertAll(entities);
        db.retellingDao().clearForCampaign(campaignId);
        db.retellingDao().upsertAll(retellings);
        db.memberDao().clearForCampaign(campaignId);
        db.memberDao().upsertAll(members);
        if (campaign != null) {
            CampaignRoom existing = db.campaignDao().find(campaignId);
            if (existing != null) {
                campaign.hidden = existing.hidden;
                if (campaign.myPermissions == null || campaign.myPermissions.isEmpty()) {
                    campaign.myPermissions = existing.myPermissions;
                }
                if (campaign.myRole == null) {
                    campaign.myRole = existing.myRole;
                }
            }
            campaign.latestRevision = latest;
            campaign.entityCount = entities.size();
            campaign.retellingCount = retellings.size();
            campaign.memberCount = members.size();
            db.campaignDao().upsert(campaign);
        }

        // Очередь уже ушла на сервер: перед snapshot выполнялся push.
        db.pendingChangeDao().clearForCampaign(campaignId);
        conflictObjects.clear();
        SyncStateRoom state = ensureState(campaignId);
        state.lastPulledRevision = latest;
        state.latestRevision = latest;
        state.lastSyncAt = DateFormats.nowIso();
        state.lastError = null;
        db.syncStateDao().upsert(state);

        status.postValue(SyncStatus.IDLE);
        return SyncResult.ok(0, 0, entities.size() + retellings.size() + members.size(), latest);
    }

    private void addEntities(List<EntityRoom> out, @Nullable List<? extends EntityDto> page,
                             EntityType kind, String campaignId) {
        if (page == null) {
            return;
        }
        for (EntityDto dto : page) {
            if (dto != null && dto.id != null) {
                out.add(Mappers.toRoom(dto, kind, campaignId));
            }
        }
    }

    // --- Вспомогательное ----------------------------------------------------

    private SyncStateRoom ensureState(String campaignId) {
        SyncStateRoom state = db.syncStateDao().find(campaignId);
        if (state == null) {
            state = new SyncStateRoom();
            state.campaignId = campaignId;
            state.lastPulledRevision = 0L;
            db.syncStateDao().upsert(state);
        }
        return state;
    }

    private long currentRevision(String campaignId) {
        CampaignRoom campaign = db.campaignDao().find(campaignId);
        return campaign == null ? 0L : campaign.latestRevision;
    }

    private void publishFailure(String campaignId, @Nullable String message, boolean offline) {
        if (campaignId != null && !campaignId.isEmpty()) {
            db.syncStateDao().markError(campaignId, message == null ? "ошибка" : message);
        }
        status.postValue(offline ? SyncStatus.OFFLINE : SyncStatus.ERROR);
        lastResult.postValue(SyncResult.failure(message));
    }

    private static String updatedAt(JsonObject payload, String fallback) {
        if (payload != null && payload.has("updatedAt") && !payload.get("updatedAt").isJsonNull()) {
            return payload.get("updatedAt").getAsString();
        }
        return fallback == null ? DateFormats.nowIso() : fallback;
    }

    private JsonObject parsePayload(String json) {
        if (json == null || json.isEmpty()) {
            return new JsonObject();
        }
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    public static String describe(ApiException e) {
        if (e == null) {
            return "Неизвестная ошибка";
        }
        if (e.isNetwork()) {
            return "Нет связи с сервером";
        }
        String message = e.getServerMessage();
        return message == null || message.isEmpty() ? e.getCode() : message;
    }
}
