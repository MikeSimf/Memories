package ru.dndmemory.ui.retelling;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

import ru.dndmemory.data.local.entity.RetellingDraftRoom;
import ru.dndmemory.data.local.entity.RetellingRoom;
import ru.dndmemory.data.remote.ApiException;
import ru.dndmemory.data.repo.RetellingRepository;
import ru.dndmemory.sync.SyncScheduler;
import ru.dndmemory.util.RepoCallback;
import ru.dndmemory.util.Resource;

/**
 * Вкладка пересказов: опубликованные пришли с сервера, черновики лежат только
 * на устройстве (docs/sync.md, раздел 4).
 */
public class RetellingListViewModel extends ViewModel {

    private final RetellingRepository retellings;
    private final SyncScheduler scheduler;

    private final MediatorLiveData<List<RetellingRoom>> published = new MediatorLiveData<>();
    private final MediatorLiveData<List<RetellingDraftRoom>> drafts = new MediatorLiveData<>();
    private final MutableLiveData<Resource<Void>> refreshState = new MutableLiveData<>();

    private String campaignId;
    private boolean initialized;

    public RetellingListViewModel(@NonNull RetellingRepository retellings,
                                  @NonNull SyncScheduler scheduler) {
        this.retellings = retellings;
        this.scheduler = scheduler;
    }

    public void init(@NonNull String campaignId) {
        if (initialized) {
            return;
        }
        initialized = true;
        this.campaignId = campaignId;
        published.addSource(retellings.observePublished(campaignId), published::setValue);
        drafts.addSource(retellings.observeDrafts(campaignId), drafts::setValue);
    }

    @NonNull
    public LiveData<List<RetellingRoom>> published() {
        return published;
    }

    @NonNull
    public LiveData<List<RetellingDraftRoom>> drafts() {
        return drafts;
    }

    @NonNull
    public LiveData<Resource<Void>> refreshState() {
        return refreshState;
    }

    @NonNull
    public String campaignId() {
        return campaignId;
    }

    public void refresh() {
        refreshState.setValue(Resource.loading());
        retellings.refresh(campaignId, new RepoCallback<List<RetellingRoom>>() {
            @Override
            public void onSuccess(@Nullable List<RetellingRoom> value) {
                refreshState.setValue(Resource.success(null));
            }

            @Override
            public void onError(@NonNull ApiException error) {
                refreshState.setValue(Resource.error(error));
            }
        });
    }

    public void syncNow() {
        scheduler.syncNow(campaignId);
    }

    public void deleteDraft(@NonNull String draftId) {
        retellings.deleteDraft(draftId, null);
    }

    public void clearRefreshState() {
        refreshState.setValue(null);
    }
}
