package ru.dndmemory.ui.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.data.model.EntityType;
import ru.dndmemory.data.remote.ApiException;
import ru.dndmemory.data.repo.EntityRepository;
import ru.dndmemory.sync.SyncScheduler;
import ru.dndmemory.util.RepoCallback;
import ru.dndmemory.util.Resource;

/** Вкладка объектов одного типа: герои, персонажи, локации или предметы. */
public class EntityListViewModel extends ViewModel {

    private final EntityRepository entities;
    private final SyncScheduler scheduler;

    private final MediatorLiveData<List<EntityRoom>> items = new MediatorLiveData<>();
    private final MutableLiveData<Resource<Void>> refreshState = new MutableLiveData<>();

    private String campaignId;
    private EntityType type;
    private boolean initialized;

    public EntityListViewModel(@NonNull EntityRepository entities, @NonNull SyncScheduler scheduler) {
        this.entities = entities;
        this.scheduler = scheduler;
    }

    /** Вызывается один раз: ViewModel переживает пересоздание View. */
    public void init(@NonNull String campaignId, @NonNull EntityType type) {
        if (initialized) {
            return;
        }
        initialized = true;
        this.campaignId = campaignId;
        this.type = type;
        items.addSource(entities.observeByType(campaignId, type), items::setValue);
    }

    @NonNull
    public LiveData<List<EntityRoom>> items() {
        return items;
    }

    @NonNull
    public LiveData<Resource<Void>> refreshState() {
        return refreshState;
    }

    @NonNull
    public EntityType type() {
        return type;
    }

    @NonNull
    public String campaignId() {
        return campaignId;
    }

    /** Онлайн-обновление списка; фоновая синхронизация идёт своим ходом. */
    public void refresh() {
        refreshState.setValue(Resource.loading());
        entities.refresh(campaignId, new RepoCallback<List<EntityRoom>>() {
            @Override
            public void onSuccess(@Nullable List<EntityRoom> value) {
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

    public void clearRefreshState() {
        refreshState.setValue(null);
    }
}
