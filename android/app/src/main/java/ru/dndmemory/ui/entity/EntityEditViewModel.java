package ru.dndmemory.ui.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.data.model.EntityType;
import ru.dndmemory.data.remote.ApiException;
import ru.dndmemory.data.repo.EntityRepository;
import ru.dndmemory.ui.common.NameIndex;
import ru.dndmemory.util.AppExecutors;
import ru.dndmemory.util.RepoCallback;
import ru.dndmemory.util.Resource;

/**
 * Редактор объекта. Все изменения остаются локальными: запись в Room плюс
 * постановка в очередь синхронизации (docs/sync.md, раздел 4).
 */
public class EntityEditViewModel extends ViewModel {

    /** Всё, что нужно экрану: сам объект, указатель имён и список для ссылок. */
    public static final class EditState {

        public final EntityRoom entity;
        public final NameIndex nameIndex;
        public final List<EntityRoom> candidates;

        EditState(@NonNull EntityRoom entity, @NonNull NameIndex nameIndex,
                  @NonNull List<EntityRoom> candidates) {
            this.entity = entity;
            this.nameIndex = nameIndex;
            this.candidates = candidates;
        }
    }

    private final EntityRepository entities;
    private final AppExecutors executors;

    private final MutableLiveData<EditState> state = new MutableLiveData<>();
    private final MutableLiveData<Resource<Void>> actionState = new MutableLiveData<>();
    private final MutableLiveData<Boolean> finishRequested = new MutableLiveData<>(false);

    private EntityRoom draft;
    private String campaignId;
    private boolean started;

    public EntityEditViewModel(@NonNull EntityRepository entities, @NonNull AppExecutors executors) {
        this.entities = entities;
        this.executors = executors;
    }

    public void start(@NonNull final String campaignId, @Nullable final String entityId,
                      @Nullable final EntityType type, final boolean createNew) {
        if (started) {
            return;
        }
        started = true;
        this.campaignId = campaignId;
        executors.io(() -> {
            EntityRoom room = null;
            if (!createNew && entityId != null) {
                room = entities.find(entityId);
            }
            if (room == null) {
                room = entities.createDraft(campaignId, type);
            }
            if (room.attrs == null) {
                room.attrs = new HashMap<>();
            }
            if (room.tags == null) {
                room.tags = new ArrayList<>();
            }
            draft = room;
            List<EntityRoom> all = entities.findAllNow(campaignId);
            return new EditState(room, NameIndex.of(all), all);
        }, new RepoCallback<EditState>() {
            @Override
            public void onSuccess(@Nullable EditState value) {
                state.setValue(value);
            }

            @Override
            public void onError(@NonNull ApiException error) {
                // Локальное чтение почти не падает; показываем ошибку экрану.
                actionState.setValue(Resource.error(error));
            }
        });
    }

    @NonNull
    public LiveData<EditState> state() {
        return state;
    }

    @NonNull
    public LiveData<Resource<Void>> actionState() {
        return actionState;
    }

    @NonNull
    public LiveData<Boolean> finishRequested() {
        return finishRequested;
    }

    public void setType(@Nullable EntityType type) {
        if (draft == null || type == null) {
            return;
        }
        draft.type = type.name();
    }

    public void setName(@Nullable String name) {
        if (draft != null) {
            draft.name = name == null ? "" : name.trim();
        }
    }

    public void setSummary(@Nullable String summary) {
        if (draft != null) {
            draft.summary = summary == null ? "" : summary.trim();
        }
    }

    public void setDescription(@Nullable String description) {
        if (draft != null) {
            draft.description = description == null ? "" : description;
        }
    }

    public void setTagsCsv(@Nullable String csv) {
        if (draft == null) {
            return;
        }
        List<String> tags = new ArrayList<>();
        if (csv != null) {
            for (String part : csv.split(",")) {
                String tag = part.trim();
                if (!tag.isEmpty()) {
                    tags.add(tag);
                }
            }
        }
        draft.tags = tags;
    }

    public void setAttrs(@NonNull Map<String, String> attrs) {
        if (draft != null) {
            draft.attrs = new HashMap<>(attrs);
        }
    }

    public void save() {
        if (draft == null) {
            return;
        }
        if (draft.name == null || draft.name.trim().isEmpty()) {
            actionState.setValue(Resource.error(
                    ru.dndmemory.data.remote.ApiException.unexpected(
                            new IllegalArgumentException("empty name"))));
            return;
        }
        actionState.setValue(Resource.loading());
        entities.save(draft, new RepoCallback<EntityRoom>() {
            @Override
            public void onSuccess(@Nullable EntityRoom value) {
                actionState.setValue(Resource.success(null));
                finishRequested.setValue(true);
            }

            @Override
            public void onError(@NonNull ApiException error) {
                actionState.setValue(Resource.error(error));
            }
        });
    }

    public void delete() {
        if (draft == null || draft.id == null) {
            return;
        }
        actionState.setValue(Resource.loading());
        entities.delete(draft.id, new RepoCallback<Void>() {
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

    @Nullable
    public String campaignId() {
        return campaignId;
    }

    public void clearActionState() {
        actionState.setValue(null);
    }
}
