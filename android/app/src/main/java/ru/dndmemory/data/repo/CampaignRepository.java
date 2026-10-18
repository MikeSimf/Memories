package ru.dndmemory.data.repo;

import androidx.lifecycle.LiveData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ru.dndmemory.data.Mappers;
import ru.dndmemory.data.local.AppDatabase;
import ru.dndmemory.data.local.entity.CampaignRoom;
import ru.dndmemory.data.model.ChangeOp;
import ru.dndmemory.data.remote.ApiCall;
import ru.dndmemory.data.remote.ApiService;
import ru.dndmemory.data.remote.dto.CampaignDto;
import ru.dndmemory.data.remote.dto.PatchCampaignRequest;
import ru.dndmemory.util.AppExecutors;
import ru.dndmemory.util.DateFormats;
import ru.dndmemory.util.RepoCallback;

/**
 * Кампании. Интерфейс читает только Room; сеть наполняет базу при явном
 * обновлении или при синхронизации.
 */
public class CampaignRepository {

    private final AppDatabase db;
    private final ApiService api;
    private final PendingWriter writer;
    private final AppExecutors exec;

    public CampaignRepository(AppDatabase db, ApiService api, PendingWriter writer, AppExecutors exec) {
        this.db = db;
        this.api = api;
        this.writer = writer;
        this.exec = exec;
    }

    public LiveData<List<CampaignRoom>> observeCampaigns() {
        return db.campaignDao().observeAll();
    }

    public LiveData<CampaignRoom> observeCampaign(String campaignId) {
        return db.campaignDao().observe(campaignId);
    }

    public CampaignRoom find(String campaignId) {
        return db.campaignDao().find(campaignId);
    }

    /** Список кампаний, где я участник (GET /api/campaigns). */
    public void refresh(RepoCallback<List<CampaignRoom>> callback) {
        exec.io(() -> {
            List<CampaignDto> remote = ApiCall.execute(api.campaigns());
            List<CampaignRoom> rooms = new ArrayList<>();
            if (remote != null) {
                for (CampaignDto dto : remote) {
                    if (dto != null && dto.id != null) {
                        CampaignRoom room = Mappers.toRoom(dto);
                        // Список отдаёт только myRole, без myPermissions: не затираем
                        // права, полученные ранее из карточки кампании.
                        CampaignRoom local = db.campaignDao().find(room.id);
                        if (local != null) {
                            if (room.myPermissions == null || room.myPermissions.isEmpty()) {
                                room.myPermissions = local.myPermissions;
                            }
                            if (room.myRole == null) {
                                room.myRole = local.myRole;
                            }
                        }
                        rooms.add(room);
                    }
                }
            }
            db.campaignDao().upsertAll(rooms);

            // Локально удаляем кампании, которых больше нет на сервере.
            Set<String> remoteIds = new HashSet<>();
            for (CampaignRoom room : rooms) {
                remoteIds.add(room.id);
            }
            for (CampaignRoom local : db.campaignDao().findAllNow()) {
                if (!remoteIds.contains(local.id)) {
                    db.campaignDao().delete(local.id);
                }
            }
            return rooms;
        }, callback);
    }

    public void refreshDetails(String campaignId, RepoCallback<CampaignRoom> callback) {
        exec.io(() -> {
            CampaignDto dto = ApiCall.execute(api.campaign(campaignId));
            CampaignRoom room = Mappers.toRoom(dto);
            if (room.id == null || room.id.isEmpty()) {
                room.id = campaignId;
            }
            CampaignRoom local = db.campaignDao().find(campaignId);
            if (local != null) {
                // Локальные счётчики и дата не должны теряться при частичном ответе.
                room.latestRevision = Math.max(room.latestRevision, local.latestRevision);
            }
            db.campaignDao().upsert(room);
            return room;
        }, callback);
    }

    public void create(String title, String description, RepoCallback<CampaignRoom> callback) {
        exec.io(() -> {
            CampaignDto dto = ApiCall.execute(api.createCampaign(
                    new ru.dndmemory.data.remote.dto.CreateCampaignRequest(title, description)));
            CampaignRoom room = Mappers.toRoom(dto);
            db.campaignDao().upsert(room);
            return room;
        }, callback);
    }

    public void rename(CampaignRoom campaign, String title, String description,
                       RepoCallback<CampaignRoom> callback) {
        exec.io(() -> {
            CampaignRoom local = db.campaignDao().find(campaign.id);
            if (local == null) {
                local = campaign;
            }
            local.title = title;
            local.description = description;
            local.updatedAt = DateFormats.nowIso();
            db.campaignDao().upsert(local);
            writer.campaign(local, ChangeOp.UPSERT);
            return local;
        }, callback);
    }

    public void delete(String campaignId, RepoCallback<Void> callback) {
        exec.io(() -> {
            ApiCall.execute(api.deleteCampaign(campaignId));
            // Локальные данные кампании уходят вместе с ней.
            db.entityDao().clearForCampaign(campaignId);
            db.retellingDao().clearForCampaign(campaignId);
            db.retellingDraftDao().clearForCampaign(campaignId);
            db.memberDao().clearForCampaign(campaignId);
            db.pendingChangeDao().clearForCampaign(campaignId);
            db.syncStateDao().clearForCampaign(campaignId);
            db.campaignDao().delete(campaignId);
            return null;
        }, callback);
    }
}
