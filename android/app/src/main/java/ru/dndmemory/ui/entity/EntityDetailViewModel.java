package ru.dndmemory.ui.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.data.remote.ApiException;
import ru.dndmemory.data.repo.EntityRepository;
import ru.dndmemory.ui.common.NameIndex;
import ru.dndmemory.util.RepoCallback;
import ru.dndmemory.util.Resource;

/** Карточка объекта: данные и указатель имён для ссылок {@code [[Имя]]}. */
public class EntityDetailViewModel extends ViewModel {

    private final EntityRepository entities;

    private final MutableLiveData<String> entityId = new MutableLiveData<>();
    private final MediatorLiveData<EntityRoom> entity = new MediatorLiveData<>();
    private LiveData<EntityRoom> entitySource;
    private final MutableLiveData<NameIndex> nameIndex = new MutableLiveData<>();
    /** Все объекты кампании: по ним считаются упоминания в карточке. */
    private final MutableLiveData<List<EntityRoom>> allEntities = new MutableLiveData<>();
    private final MutableLiveData<Resource<Void>> actionState = new MutableLiveData<>();
    private final MutableLiveData<Boolean> finishRequested = new MutableLiveData<>(false);

    private String campaignId;

    public EntityDetailViewModel(@NonNull EntityRepository entities) {
        this.entities = entities;
    }

    public void start(@NonNull String campaignId, @NonNull String entityId) {
        this.campaignId = campaignId;
        this.entityId.setValue(entityId);
        if (entitySource != null) {
            entity.removeSource(entitySource);
        }
        entitySource = entities.observe(entityId);
        entity.addSource(entitySource, entity::setValue);
        loadIndex();
    }

    @NonNull
    public LiveData<EntityRoom> entity() {
        return entity;
    }

    @NonNull
    public LiveData<NameIndex> nameIndex() {
        return nameIndex;
    }

    @NonNull
    public LiveData<List<EntityRoom>> allEntities() {
        return allEntities;
    }

    @NonNull
    public LiveData<Resource<Void>> actionState() {
        return actionState;
    }

    @NonNull
    public LiveData<Boolean> finishRequested() {
        return finishRequested;
    }

    @Nullable
    public String campaignId() {
        return campaignId;
    }

    private void loadIndex() {
        entities.loadEntities(campaignId, new RepoCallback<List<EntityRoom>>() {
            @Override
            public void onSuccess(@Nullable List<EntityRoom> value) {
                allEntities.setValue(value);
                nameIndex.setValue(NameIndex.of(value));
            }

            @Override
            public void onError(@NonNull ApiException error) {
                allEntities.setValue(java.util.Collections.emptyList());
                nameIndex.setValue(NameIndex.of(null));
            }
        });
    }

    public void delete() {
        actionState.setValue(Resource.loading());
        entities.delete(entityId.getValue(), new RepoCallback<Void>() {
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

    /** «Взять версию сервера» после расхождения. */
    public void acceptServerVersion() {
        entities.acceptServerVersion(entityId.getValue(), new RepoCallback<EntityRoom>() {
            @Override
            public void onSuccess(@Nullable EntityRoom value) {
                actionState.setValue(Resource.success(null));
            }

            @Override
            public void onError(@NonNull ApiException error) {
                actionState.setValue(Resource.error(error));
            }
        });
    }

    /** «Оставить мою версию»: повторная отправка с новым временем. */
    public void overwriteWithLocal() {
        entities.overwriteWithLocal(entityId.getValue(), new RepoCallback<EntityRoom>() {
            @Override
            public void onSuccess(@Nullable EntityRoom value) {
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
