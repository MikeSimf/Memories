package ru.dndmemory.ui.retelling;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.data.local.entity.RetellingRoom;
import ru.dndmemory.data.remote.ApiException;
import ru.dndmemory.data.repo.EntityRepository;
import ru.dndmemory.data.repo.RetellingRepository;
import ru.dndmemory.ui.common.NameIndex;
import ru.dndmemory.util.RepoCallback;
import ru.dndmemory.util.Resource;

/** Просмотр опубликованного пересказа. */
public class RetellingDetailViewModel extends ViewModel {

    private final RetellingRepository retellings;
    private final EntityRepository entities;

    private final MutableLiveData<String> retellingId = new MutableLiveData<>();
    private final MediatorLiveData<RetellingRoom> retelling = new MediatorLiveData<>();
    private LiveData<RetellingRoom> retellingSource;
    private final MutableLiveData<NameIndex> nameIndex = new MutableLiveData<>();
    private final MutableLiveData<Resource<Void>> actionState = new MutableLiveData<>();
    private final MutableLiveData<Boolean> finishRequested = new MutableLiveData<>(false);

    private String campaignId;

    public RetellingDetailViewModel(@NonNull RetellingRepository retellings,
                                    @NonNull EntityRepository entities) {
        this.retellings = retellings;
        this.entities = entities;
    }

    public void start(@NonNull String campaignId, @NonNull String retellingId) {
        this.campaignId = campaignId;
        this.retellingId.setValue(retellingId);
        if (retellingSource != null) {
            retelling.removeSource(retellingSource);
        }
        retellingSource = retellings.observe(retellingId);
        retelling.addSource(retellingSource, retelling::setValue);
        entities.loadEntities(campaignId, new RepoCallback<List<EntityRoom>>() {
            @Override
            public void onSuccess(@Nullable List<EntityRoom> value) {
                nameIndex.setValue(NameIndex.of(value));
            }

            @Override
            public void onError(@NonNull ApiException error) {
                nameIndex.setValue(NameIndex.of(null));
            }
        });
    }

    @NonNull
    public LiveData<RetellingRoom> retelling() {
        return retelling;
    }

    @NonNull
    public LiveData<NameIndex> nameIndex() {
        return nameIndex;
    }

    @NonNull
    public LiveData<Resource<Void>> actionState() {
        return actionState;
    }

    @NonNull
    public LiveData<Boolean> finishRequested() {
        return finishRequested;
    }

    public void delete() {
        actionState.setValue(Resource.loading());
        retellings.delete(retellingId.getValue(), new RepoCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void value) {
                actionState.setValue(Resource.success(null));
                finishRequested.setValue(true);
            }

            @Override
            public void onError(@NonNull ApiException error) {
                actionState.setValue(Resource.error(error));
            }
        });
    }

    public void acceptServerVersion() {
        retellings.acceptServerVersion(retellingId.getValue(), new RepoCallback<RetellingRoom>() {
            @Override
            public void onSuccess(@Nullable RetellingRoom value) {
                actionState.setValue(Resource.success(null));
            }

            @Override
            public void onError(@NonNull ApiException error) {
                actionState.setValue(Resource.error(error));
            }
        });
    }

    public void overwriteWithLocal() {
        retellings.overwriteWithLocal(retellingId.getValue(), new RepoCallback<RetellingRoom>() {
            @Override
            public void onSuccess(@Nullable RetellingRoom value) {
                actionState.setValue(Resource.success(null));
            }

            @Override
            public void onError(@NonNull ApiException error) {
                actionState.setValue(Resource.error(error));
            }
        });
    }

    public void clearActionState() {
        actionState.setValue(null);
    }
}
