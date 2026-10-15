package ru.dndmemory.data.repo;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import java.util.ArrayList;
import java.util.List;

import ru.dndmemory.data.Mappers;
import ru.dndmemory.data.local.AppDatabase;
import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.data.model.ChangeOp;
import ru.dndmemory.data.model.EntityLink;
import ru.dndmemory.data.model.EntityType;
import ru.dndmemory.data.remote.ApiCall;
import ru.dndmemory.data.remote.ApiService;
import ru.dndmemory.data.remote.dto.EntityDto;
import ru.dndmemory.util.AppExecutors;
import ru.dndmemory.util.DateFormats;
import ru.dndmemory.util.Ids;
import ru.dndmemory.util.RepoCallback;
import ru.dndmemory.util.WikiMarkdown;

/**
 * Объекты кампании. Все правки — локальные: запись в Room плюс постановка в
 * {@code pending_changes}. На сервер их отправит {@code SyncEngine}.
 */
public class EntityRepository {

    private final AppDatabase db;
    private final ApiService api;
    private final PendingWriter writer;
    private final AppExecutors exec;

    public EntityRepository(AppDatabase db, ApiService api, PendingWriter writer, AppExecutors exec) {
        this.db = db;
        this.api = api;
        this.writer = writer;
        this.exec = exec;
    }

    public LiveData<List<EntityRoom>> observeAll(String campaignId) {
        return db.entityDao().observeAll(campaignId);
    }

    public LiveData<List<EntityRoom>> observeByType(String campaignId, EntityType type) {
        if (type == null) {
            return db.entityDao().observeAll(campaignId);
        }
        return db.entityDao().observeByType(campaignId, type.name());
    }

    public LiveData<EntityRoom> observe(String entityId) {
        return db.entityDao().observe(entityId);
    }

    @Nullable
    public EntityRoom find(String entityId) {
        return db.entityDao().find(entityId);
    }

    public List<EntityRoom> findAllNow(String campaignId) {
        return db.entityDao().findAllNow(campaignId);
    }

    public List<EntityRoom> findByTypeNow(String campaignId, EntityType type) {
        return db.entityDao().findByTypeNow(campaignId, type.name());
    }

    /** Офлайн-поиск по локальной базе — работает без сети (docs/api.md, раздел 8). */
    public LiveData<List<EntityRoom>> search(String campaignId, @Nullable EntityType type, String query) {
        String pattern = "%" + (query == null ? "" : query.trim()) + "%";
        if (type == null) {
            return db.entityDao().search(campaignId, pattern);
        }
        return db.entityDao().searchByType(campaignId, type.name(), pattern);
    }

    /** Новый объект: id и clientId генерирует клиент, в базу он ещё не записан. */
    public EntityRoom createDraft(String campaignId, EntityType type) {
        EntityRoom room = new EntityRoom();
        room.id = Ids.uuid();
        room.clientId = room.id;
        room.campaignId = campaignId;
        room.type = (type == null ? EntityType.NPC : type).name();
        room.name = "";
        room.summary = "";
        room.description = "";
        room.createdAt = DateFormats.nowIso();
        room.updatedAt = room.createdAt;
        room.revision = 0L;
        room.dirty = true;
        return room;
    }

    public void save(EntityRoom room, RepoCallback<EntityRoom> callback) {
        exec.io(() -> {
            room.updatedAt = DateFormats.nowIso();
            if (room.createdAt == null || room.createdAt.isEmpty()) {
                room.createdAt = room.updatedAt;
            }
            if (room.clientId == null || room.clientId.isEmpty()) {
                room.clientId = room.id;
            }
            room.dirty = true;
            room.links = resolveLinks(room.campaignId, room.description, room.summary);
            db.entityDao().upsert(room);
            writer.entity(room, ChangeOp.UPSERT);
            return room;
        }, callback);
    }

    public void delete(String entityId, RepoCallback<Void> callback) {
        exec.io(() -> {
            EntityRoom room = db.entityDao().find(entityId);
            if (room == null) {
                return null;
            }
            room.updatedAt = DateFormats.nowIso();
            db.entityDao().markDeleted(entityId, room.updatedAt);
            room.deleted = true;
            room.dirty = true;
            writer.entity(room, ChangeOp.DELETE);
            return null;
        }, callback);
    }

    /** «Оставить серверную версию»: снимаем расхождение и тянем объект заново. */
    public void acceptServerVersion(String entityId, RepoCallback<EntityRoom> callback) {
        exec.io(() -> {
            EntityRoom local = db.entityDao().find(entityId);
            if (local == null) {
                throw ru.dndmemory.data.remote.ApiException.unexpected(
                        new IllegalStateException("Объект не найден локально"));
            }
            EntityDto dto = ApiCall.execute(api.entity(local.campaignId, entityId));
            EntityRoom fresh = Mappers.toRoom(dto, local.campaignId);
            fresh.dirty = false;
            fresh.conflictJson = null;
            db.entityDao().upsert(fresh);
            return fresh;
        }, callback);
    }

    /** «Перезаписать своей версией»: свежий updatedAt и повторная постановка в очередь. */
    public void overwriteWithLocal(String entityId, RepoCallback<EntityRoom> callback) {
        exec.io(() -> {
            EntityRoom room = db.entityDao().find(entityId);
            if (room == null) {
                throw ru.dndmemory.data.remote.ApiException.unexpected(
                        new IllegalStateException("Объект не найден локально"));
            }
            room.updatedAt = DateFormats.nowIso();
            room.conflictJson = null;
            room.dirty = true;
            room.deleted = false;
            db.entityDao().upsert(room);
            writer.entity(room, ChangeOp.UPSERT);
            return room;
        }, callback);
    }

    /** Полная перезагрузка списка объектов кампании (онлайн-обновление). */
    public void refresh(String campaignId, RepoCallback<List<EntityRoom>> callback) {
        exec.io(() -> {
            List<EntityDto> remote = ApiCall.execute(
                    api.entities(campaignId, null, null, null, 500, null));
            List<EntityRoom> rooms = new ArrayList<>();
            if (remote != null) {
                for (EntityDto dto : remote) {
                    if (dto == null || dto.id == null) {
                        continue;
                    }
                    EntityRoom incoming = Mappers.toRoom(dto, campaignId);
                    EntityRoom local = db.entityDao().find(incoming.id);
                    if (local != null && local.dirty) {
                        // Локальные неотправленные правки не затираем.
                        continue;
                    }
                    db.entityDao().upsert(incoming);
                    rooms.add(incoming);
                }
            }
            return rooms;
        }, callback);
    }

    /**
     * Локальное разрешение ссылок {@code [[Имя]]} по своей базе — правило то же,
     * что у сервера (docs/sync.md, раздел 7).
     */
    public List<EntityLink> resolveLinks(String campaignId, String... texts) {
        return LinkResolver.resolve(db.entityDao(), campaignId, texts);
    }

    @Nullable
    public EntityRoom resolveByName(String campaignId, String name) {
        return db.entityDao().resolveByName(campaignId, name);
    }
}
