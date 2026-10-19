package ru.dndmemory.data.repo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import java.util.ArrayList;
import java.util.List;

import ru.dndmemory.data.Mappers;
import ru.dndmemory.data.local.AppDatabase;
import ru.dndmemory.data.local.entity.MaterialRoom;
import ru.dndmemory.data.model.ChangeOp;
import ru.dndmemory.data.remote.ApiCall;
import ru.dndmemory.data.remote.ApiException;
import ru.dndmemory.data.remote.ApiService;
import ru.dndmemory.data.remote.dto.MaterialDto;
import ru.dndmemory.data.remote.dto.ObjectListDto;
import ru.dndmemory.util.AppExecutors;
import ru.dndmemory.util.DateFormats;
import ru.dndmemory.util.Ids;
import ru.dndmemory.util.RepoCallback;

/**
 * Фото-материалы кампании: локальный кэш плюс очередь синхронизации.
 * Работает офлайн так же, как объекты справочника: правка ложится в Room
 * и в {@code pending_changes}, отправляется при появлении сети.
 */
public class MaterialRepository {

    private final AppDatabase db;
    private final ApiService api;
    private final PendingWriter writer;
    private final AppExecutors exec;

    public MaterialRepository(@NonNull AppDatabase db, @NonNull ApiService api,
                              @NonNull PendingWriter writer, @NonNull AppExecutors exec) {
        this.db = db;
        this.api = api;
        this.writer = writer;
        this.exec = exec;
    }

    @NonNull
    public LiveData<List<MaterialRoom>> observe(@NonNull String campaignId) {
        return db.materialDao().observe(campaignId);
    }

    @Nullable
    public MaterialRoom find(@NonNull String id) {
        return db.materialDao().find(id);
    }

    /** Черновик нового материала: id генерирует клиент. */
    @NonNull
    public MaterialRoom createDraft(@NonNull String campaignId) {
        MaterialRoom room = new MaterialRoom();
        room.id = Ids.uuid();
        room.campaignId = campaignId;
        room.name = "";
        room.description = "";
        room.createdAt = DateFormats.nowIso();
        room.updatedAt = room.createdAt;
        room.revision = 0L;
        room.dirty = true;
        return room;
    }

    public void save(@NonNull MaterialRoom room, @NonNull RepoCallback<MaterialRoom> callback) {
        exec.io(() -> {
            room.updatedAt = DateFormats.nowIso();
            if (room.createdAt == null || room.createdAt.isEmpty()) {
                room.createdAt = room.updatedAt;
            }
            room.dirty = true;
            db.materialDao().upsert(room);
            writer.material(room, ChangeOp.UPSERT);
            return room;
        }, callback);
    }

    public void delete(@NonNull String materialId, @NonNull RepoCallback<Void> callback) {
        exec.io(() -> {
            MaterialRoom room = db.materialDao().find(materialId);
            if (room != null) {
                room.updatedAt = DateFormats.nowIso();
                db.materialDao().markDeleted(materialId, room.updatedAt);
                room.deleted = true;
                room.dirty = true;
                writer.material(room, ChangeOp.DELETE);
            }
            return null;
        }, callback);
    }

    /** Онлайн-обновление списка; без сети остаётся локальный кэш. */
    public void refresh(@NonNull String campaignId, @NonNull RepoCallback<List<MaterialRoom>> callback) {
        exec.io(() -> {
            ObjectListDto<MaterialDto> page = ApiCall.execute(
                    api.materials(campaignId, null, null, 200, null));
            List<MaterialRoom> rooms = new ArrayList<>();
            if (page != null && page.items != null) {
                for (MaterialDto dto : page.items) {
                    if (dto == null || dto.id == null) {
                        continue;
                    }
                    MaterialRoom incoming = Mappers.toRoom(dto, campaignId);
                    MaterialRoom local = db.materialDao().find(incoming.id);
                    if (local != null && local.dirty) {
                        continue; // не затираем неотправленную правку
                    }
                    db.materialDao().upsert(incoming);
                    rooms.add(incoming);
                }
            }
            return rooms;
        }, callback);
    }

    /** Сетевые ошибки не должны ломать список: интерфейс живёт на кэше. */
    public void refreshQuietly(@NonNull String campaignId) {
        refresh(campaignId, new RepoCallback<List<MaterialRoom>>() {
            @Override
            public void onSuccess(@Nullable List<MaterialRoom> value) {
            }

            @Override
            public void onError(@NonNull ApiException error) {
            }
        });
    }
}
