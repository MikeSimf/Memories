package ru.dndmemory.ui.retelling;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.data.local.entity.RetellingDraftRoom;
import ru.dndmemory.data.local.entity.RetellingRoom;
import ru.dndmemory.data.model.EntityType;
import ru.dndmemory.data.remote.ApiException;
import ru.dndmemory.data.repo.EntityRepository;
import ru.dndmemory.data.repo.RetellingRepository;
import ru.dndmemory.ui.common.NameIndex;
import ru.dndmemory.util.AppExecutors;
import ru.dndmemory.util.DateFormats;
import ru.dndmemory.util.RepoCallback;
import ru.dndmemory.util.Resource;

/**
 * Редактор пересказа. Пока пользователь не нажал «Опубликовать», текст живёт
 * в {@code retelling_drafts} и на сервер не уходит: сервер отвечает на
 * {@code status: "DRAFT"} кодом {@code 422 draft_not_synced}.
 */
public class RetellingEditViewModel extends ViewModel {

    public static final class EditState {

        public final RetellingDraftRoom draft;
        public final NameIndex nameIndex;
        public final List<EntityRoom> heroes;

        EditState(@NonNull RetellingDraftRoom draft, @NonNull NameIndex nameIndex,
                  @NonNull List<EntityRoom> heroes) {
            this.draft = draft;
            this.nameIndex = nameIndex;
            this.heroes = heroes;
        }
    }

    private final RetellingRepository retellings;
    private final EntityRepository entities;
    private final AppExecutors executors;

    private final MutableLiveData<EditState> state = new MutableLiveData<>();
    private final MutableLiveData<Resource<Void>> actionState = new MutableLiveData<>();
    private final MutableLiveData<Boolean> finishRequested = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> published = new MutableLiveData<>(false);

    private RetellingDraftRoom draft;
    private String campaignId;
    private boolean started;

    public RetellingEditViewModel(@NonNull RetellingRepository retellings,
                                  @NonNull EntityRepository entities,
                                  @NonNull AppExecutors executors) {
        this.retellings = retellings;
        this.entities = entities;
        this.executors = executors;
    }

    public void start(@NonNull final String campaignId, @Nullable final String draftId,
                      @Nullable final String retellingId, final boolean createNew) {
        if (started) {
            return;
        }
        started = true;
        this.campaignId = campaignId;
        executors.io(() -> {
            RetellingDraftRoom found = null;
            if (!createNew && draftId != null) {
                found = retellings.findDraft(draftId);
            }
            if (!createNew && found == null && retellingId != null) {
                found = retellings.findDraftByRemoteId(retellingId);
                if (found == null) {
                    RetellingRoom released = retellings.find(retellingId);
                    if (released != null) {
                        found = retellings.createDraftFor(released);
                    }
                }
            }
            if (found == null) {
                found = retellings.createDraft(campaignId, null);
            }
            if (found.title == null) {
                found.title = "";
            }
            if (found.body == null) {
                found.body = "";
            }
            if (found.sessionDate == null || found.sessionDate.isEmpty()) {
                found.sessionDate = DateFormats.todayDate();
            }
            draft = found;

            List<EntityRoom> all = entities.findAllNow(campaignId);
            List<EntityRoom> heroes = entities.findByTypeNow(campaignId, EntityType.HERO);
            return new EditState(found, NameIndex.of(all), heroes);
        }, state::setValue);
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

    /** true — пересказ опубликован, false — сохранён черновик. */
    @NonNull
    public LiveData<Boolean> published() {
        return published;
    }

    public void setTitle(@Nullable String title) {
        if (draft != null) {
            draft.title = title == null ? "" : title.trim();
        }
    }

    public void setBody(@Nullable String body) {
        if (draft != null) {
            draft.body = body == null ? "" : body;
        }
    }

    public void setSessionDate(@Nullable String date) {
        if (draft != null) {
            draft.sessionDate = date == null ? "" : date.trim();
        }
    }

    public void setAuthorEntityId(@Nullable String authorEntityId) {
        if (draft != null) {
            draft.authorEntityId = authorEntityId;
        }
    }

    /** Сохраняет черновик локально — без обращения к сети. */
    public void saveDraft() {
        if (draft == null) {
            return;
        }
        actionState.setValue(Resource.loading());
        retellings.saveDraft(draft, new RepoCallback<RetellingDraftRoom>() {
            @Override
            public void onSuccess(@Nullable RetellingDraftRoom value) {
                actionState.setValue(Resource.success(null));
                published.setValue(false);
                finishRequested.setValue(true);
            }

            @Override
            public void onError(@NonNull ApiException error) {
                actionState.setValue(Resource.error(error));
            }
        });
    }

    /** Публикация: черновик становится пересказом и уходит в очередь синхронизации. */
    public void publish() {
        if (draft == null) {
            return;
        }
        actionState.setValue(Resource.loading());
        retellings.publish(draft, new RepoCallback<RetellingRoom>() {
            @Override
            public void onSuccess(@Nullable RetellingRoom value) {
                actionState.setValue(Resource.success(null));
                published.setValue(true);
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
