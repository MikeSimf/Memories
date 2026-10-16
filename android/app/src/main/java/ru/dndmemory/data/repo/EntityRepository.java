package ru.dndmemory.data.repo;

import androidx.annotation.NonNull;
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
import ru.dndmemory.data.remote.dto.CharacterDto;
import ru.dndmemory.data.remote.dto.EntityDto;
import ru.dndmemory.data.remote.dto.HeroDto;
import ru.dndmemory.data.remote.dto.ItemDto;
import ru.dndmemory.data.remote.dto.LocationDto;
import ru.dndmemory.data.remote.dto.SearchResponse;
import ru.dndmemory.util.AppExecutors;
import ru.dndmemory.util.DateFormats;
import ru.dndmemory.util.Ids;
import ru.dndmemory.util.RepoCallback;

/**
 * Объекты кампании. Все четыре класса лежат в одной таблице {@code entities}
 * с дискриминатором {@code kind}, но сеть у каждого класса своя — четыре набора
 * эндпоинтов (docs/api.md, раздел 6). Все правки локальные: запись в Room плюс
 * постановка в {@code pending_changes}, на сервер их отправит {@code SyncEngine}.
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

    public LiveData<List<EntityRoom>> observeByKind(String campaignId, @Nullable EntityType kind) {
        if (kind == null) {
            return db.entityDao().observeAll(campaignId);
        }
        return db.entityDao().observeByKind(campaignId, kind.name());
    }

    /**
     * Объекты вкладки: «Персонажи» — {@code HERO,CHARACTER}, «Локации» —
     * {@code LOCATION}, «Предметы» — {@code ITEM}. Пустой список — все классы.
     */
    public LiveData<List<EntityRoom>> observeByKinds(String campaignId, List<String> kinds) {
        if (kinds == null || kinds.isEmpty()) {
            return db.entityDao().observeAll(campaignId);
        }
        return db.entityDao().observeByKinds(campaignId, kinds);
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

    /** Объекты одного класса — например, только герои для участников и приглашений. */
    public List<EntityRoom> findByKindNow(String campaignId, EntityType kind) {
        return db.entityDao().findByKindNow(campaignId, kind.name());
    }

    public List<EntityRoom> findByKindsNow(String campaignId, List<String> kinds) {
        if (kinds == null || kinds.isEmpty()) {
            return db.entityDao().findAllNow(campaignId);
        }
        return db.entityDao().findByKindsNow(campaignId, kinds);
    }

    /** Офлайн-поиск по локальной базе — работает без сети (docs/api.md, раздел 8). */
    public LiveData<List<EntityRoom>> search(String campaignId, @Nullable EntityType kind, String query) {
        String pattern = "%" + (query == null ? "" : query.trim()) + "%";
        if (kind == null) {
            return db.entityDao().search(campaignId, pattern);
        }
        return db.entityDao().searchByKinds(campaignId, single(kind), pattern);
    }

    /** Поиск по вкладке с несколькими классами: «Персонажи» ищет героев и НПС. */
    public LiveData<List<EntityRoom>> searchByKinds(String campaignId, @Nullable List<String> kinds,
                                                   String query) {
        String pattern = "%" + (query == null ? "" : query.trim()) + "%";
        if (kinds == null || kinds.isEmpty()) {
            return db.entityDao().search(campaignId, pattern);
        }
        return db.entityDao().searchByKinds(campaignId, kinds, pattern);
    }

    /** Новый объект: id генерирует клиент, в базу он ещё не записан. */
    public EntityRoom createDraft(String campaignId, EntityType kind) {
        EntityRoom room = new EntityRoom();
        room.id = Ids.uuid();
        room.campaignId = campaignId;
        room.kind = (kind == null ? EntityType.CHARACTER : kind).name();
        room.name = "";
        room.description = "";
        room.items = new ArrayList<>();
        room.links = new ArrayList<>();
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
            room.dirty = true;
            room.links = resolveLinks(room.campaignId, room.description, room.shortDescription);
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
            EntityType kind = EntityType.fromOrCharacter(local.kind);
            EntityDto dto = fetchOne(local.campaignId, kind, entityId);
            EntityRoom fresh = Mappers.toRoom(dto, kind, local.campaignId);
            if (fresh.id == null || fresh.id.isEmpty()) {
                fresh.id = entityId;
            }
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
        refresh(campaignId, null, callback);
    }

    /**
     * Перезагрузка объектов вкладки: сервер опрашивается по каждому классу
     * отдельным эндпоинтом ({@code /heroes}, {@code /characters}, …).
     */
    public void refresh(String campaignId, @Nullable List<String> kinds,
                        RepoCallback<List<EntityRoom>> callback) {
        exec.io(() -> {
            List<EntityType> wanted = kinds == null || kinds.isEmpty()
                    ? EntityType.ALL : kindsOf(kinds);
            List<EntityRoom> rooms = new ArrayList<>();
            for (EntityType kind : wanted) {
                List<? extends EntityDto> remote = fetchList(campaignId, kind);
                if (remote == null) {
                    continue;
                }
                for (EntityDto dto : remote) {
                    if (dto == null || dto.id == null) {
                        continue;
                    }
                    EntityRoom incoming = Mappers.toRoom(dto, kind, campaignId);
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

    /** Снимок всех объектов кампании (для указателей имён и редактора ссылок). */
    public void loadEntities(String campaignId, RepoCallback<List<EntityRoom>> callback) {
        exec.io(() -> db.entityDao().findAllNow(campaignId), callback);
    }

    /** Герои кампании: нужны участникам и приглашениям (привязка к герою). */
    public void loadHeroes(String campaignId, RepoCallback<List<EntityRoom>> callback) {
        exec.io(() -> db.entityDao().findByKindNow(campaignId, EntityType.HERO.name()), callback);
    }

    /**
     * Поиск на сервере (docs/api.md, раздел 8). Локальный поиск работает всегда,
     * серверный дополняет его, когда есть сеть.
     */
    public void searchRemote(String campaignId, String query, @Nullable List<String> kinds,
                             RepoCallback<SearchResponse> callback) {
        String kindsCsv = kinds == null || kinds.isEmpty() ? null : join(kinds);
        exec.io(() -> ApiCall.execute(api.search(campaignId, query, kindsCsv, 50)), callback);
    }

    /** {@code kinds=HERO,CHARACTER} — так фильтрует сервер. */
    @NonNull
    public static String join(@NonNull List<String> kinds) {
        StringBuilder out = new StringBuilder();
        for (String kind : kinds) {
            if (kind == null || kind.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(kind);
        }
        return out.toString();
    }

    private static List<String> single(EntityType kind) {
        List<String> out = new ArrayList<>();
        out.add(kind.name());
        return out;
    }

    @NonNull
    private static List<EntityType> kindsOf(@NonNull List<String> kinds) {
        List<EntityType> out = new ArrayList<>();
        for (String kind : kinds) {
            EntityType type = EntityType.from(kind);
            if (type != null && !out.contains(type)) {
                out.add(type);
            }
        }
        return out.isEmpty() ? EntityType.ALL : out;
    }

    /** Список класса: у каждого класса кампании свой эндпоинт (docs/api.md, раздел 6). */
    @Nullable
    private List<? extends EntityDto> fetchList(String campaignId, EntityType kind)
            throws ru.dndmemory.data.remote.ApiException {
        switch (kind) {
            case HERO:
                return ApiCall.execute(api.heroes(campaignId, null, null, 500, null));
            case LOCATION:
                return ApiCall.execute(api.locations(campaignId, null, null, 500, null));
            case ITEM:
                return ApiCall.execute(api.items(campaignId, null, null, 500, null));
            case CHARACTER:
            default:
                return ApiCall.execute(api.characters(campaignId, null, null, 500, null));
        }
    }

    /** Чтение одного объекта: {@code GET .../heroes/{id}} и аналоги. */
    @Nullable
    private EntityDto fetchOne(String campaignId, EntityType kind, String entityId)
            throws ru.dndmemory.data.remote.ApiException {
        switch (kind) {
            case HERO:
                return ApiCall.execute(api.hero(campaignId, entityId));
            case LOCATION:
                return ApiCall.execute(api.location(campaignId, entityId));
            case ITEM:
                return ApiCall.execute(api.item(campaignId, entityId));
            case CHARACTER:
            default:
                return ApiCall.execute(api.character(campaignId, entityId));
        }
    }

    /**
     * Локальное разрешение ссылок {@code [[Имя]]} по своей базе — правило то же,
     * что у сервера (docs/sync.md, раздел 8). Имена ищутся по всем четырём классам.
     */
    public List<EntityLink> resolveLinks(String campaignId, String... texts) {
        return LinkResolver.resolve(db.entityDao(), campaignId, texts);
    }

    @Nullable
    public EntityRoom resolveByName(String campaignId, String name) {
        return db.entityDao().resolveByName(campaignId, name);
    }
}
