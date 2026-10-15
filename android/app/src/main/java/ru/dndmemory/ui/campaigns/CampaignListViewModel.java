package ru.dndmemory.ui.campaigns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

import ru.dndmemory.data.local.entity.CampaignRoom;
import ru.dndmemory.data.model.SyncStatus;
import ru.dndmemory.data.remote.ApiException;
import ru.dndmemory.data.repo.AuthRepository;
import ru.dndmemory.data.repo.CampaignRepository;
import ru.dndmemory.sync.SyncEngine;
import ru.dndmemory.sync.SyncScheduler;
import ru.dndmemory.util.RepoCallback;
import ru.dndmemory.util.Resource;

/** Список кампаний: локальные данные плюс обновление с сервера. */
public class CampaignListViewModel extends ViewModel {

    private final CampaignRepository campaigns;
    private final AuthRepository auth;
    private final SyncEngine syncEngine;
    private final SyncScheduler scheduler;

    private final MutableLiveData<Resource<Void>> refreshState = new MutableLiveData<>();
    private final MutableLiveData<Resource<String>> actionState = new MutableLiveData<>();

    public CampaignListViewModel(@NonNull CampaignRepository campaigns, @NonNull AuthRepository auth,
                                 @NonNull SyncEngine syncEngine, @NonNull SyncScheduler scheduler) {
        this.campaigns = campaigns;
        this.auth = auth;
        this.syncEngine = syncEngine;
        this.scheduler = scheduler;
    }

    @NonNull
    public LiveData<List<CampaignRoom>> campaigns() {
        return campaigns.observeCampaigns();
    }

    @NonNull
    public LiveData<SyncStatus> syncStatus() {
        return syncEngine.observeStatus();
    }

    @NonNull
    public LiveData<String> displayName() {
        return auth.displayName();
    }

    @NonNull
    public LiveData<Resource<Void>> refreshState() {
        return refreshState;
    }

    @NonNull
    public LiveData<Resource<String>> actionState() {
        return actionState;
    }

    public void refresh() {
        refreshState.setValue(Resource.loading());
        campaigns.refresh(new RepoCallback<List<CampaignRoom>>() {
            @Override
            public void onSuccess(@Nullable List<CampaignRoom> value) {
                refreshState.setValue(Resource.success(null));
                // После списка подтягиваем журнал изменений.
                scheduler.syncNow(null);
            }

            @Override
            public void onError(@NonNull ApiException error) {
                refreshState.setValue(Resource.error(error));
            }
        });
    }

    public void create(@NonNull String title, @NonNull String description) {
        actionState.setValue(Resource.loading());
        campaigns.create(title, description, new RepoCallback<CampaignRoom>() {
            @Override
            public void onSuccess(@Nullable CampaignRoom value) {
                actionState.setValue(Resource.success(
                        value == null ? null : value.title));
            }

            @Override
            public void onError(@NonNull ApiException error) {
                actionState.setValue(Resource.error(error));
            }
        });
    }

    public void rename(@NonNull CampaignRoom campaign, @NonNull String title,
                       @NonNull String description) {
        actionState.setValue(Resource.loading());
        campaigns.rename(campaign, title, description, new RepoCallback<CampaignRoom>() {
            @Override
            public void onSuccess(@Nullable CampaignRoom value) {
                actionState.setValue(Resource.success(value == null ? null : value.title));
            }

            @Override
            public void onError(@NonNull ApiException error) {
                actionState.setValue(Resource.error(error));
            }
        });
    }

    public void delete(@NonNull CampaignRoom campaign) {
        actionState.setValue(Resource.loading());
        campaigns.delete(campaign.id, new RepoCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void value) {
                actionState.setValue(Resource.success(campaign.title));
            }

            @Override
            public void onError(@NonNull ApiException error) {
                actionState.setValue(Resource.error(error));
            }
        });
    }

    public void syncNow() {
        scheduler.syncNow(null);
    }

    public void clearActionState() {
        actionState.setValue(null);
    }

    public void clearRefreshState() {
        refreshState.setValue(null);
    }
}
