package ru.dndmemory.ui.campaign;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import ru.dndmemory.data.local.entity.CampaignRoom;
import ru.dndmemory.data.model.SyncStatus;
import ru.dndmemory.data.repo.AuthRepository;
import ru.dndmemory.data.repo.CampaignRepository;
import ru.dndmemory.sync.SyncEngine;
import ru.dndmemory.sync.SyncScheduler;

/** Общее состояние экрана кампании: сама кампания, права, статус синхронизации. */
public class CampaignViewModel extends ViewModel {

    private final CampaignRepository campaigns;
    private final AuthRepository auth;
    private final SyncEngine syncEngine;
    private final SyncScheduler scheduler;

    private final MutableLiveData<String> campaignId = new MutableLiveData<>();
    private final LiveData<CampaignRoom> campaign;

    public CampaignViewModel(@NonNull CampaignRepository campaigns, @NonNull AuthRepository auth,
                             @NonNull SyncEngine syncEngine, @NonNull SyncScheduler scheduler) {
        this.campaigns = campaigns;
        this.auth = auth;
        this.syncEngine = syncEngine;
        this.scheduler = scheduler;
        this.campaign = Transformations.switchMap(campaignId, campaigns::observeCampaign);
    }

    public void setCampaignId(@Nullable String id) {
        campaignId.setValue(id);
    }

    @Nullable
    public String campaignId() {
        return campaignId.getValue();
    }

    @NonNull
    public LiveData<CampaignRoom> campaign() {
        return campaign;
    }

    @NonNull
    public LiveData<SyncStatus> syncStatus() {
        return syncEngine.observeStatus();
    }

    @Nullable
    public String currentUserId() {
        return auth.userId();
    }

    /** Обновляет детали кампании и запускает синхронизацию. */
    public void refresh() {
        String id = campaignId.getValue();
        if (id == null || id.isEmpty()) {
            return;
        }
        campaigns.refreshDetails(id, null);
        scheduler.syncNow(id);
    }

    public void syncNow() {
        scheduler.syncNow(campaignId.getValue());
    }
}
