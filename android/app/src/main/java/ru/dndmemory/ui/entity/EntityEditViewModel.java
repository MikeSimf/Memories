package ru.dndmemory.ui.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.data.local.entity.MemberRoom;
import ru.dndmemory.data.model.EntityType;
import ru.dndmemory.data.model.HeroItem;
import ru.dndmemory.data.remote.ApiException;
import ru.dndmemory.data.repo.EntityRepository;
import ru.dndmemory.data.repo.MemberRepository;
import ru.dndmemory.ui.common.NameIndex;
import ru.dndmemory.util.AppExecutors;
import ru.dndmemory.util.RepoCallback;
import ru.dndmemory.util.Resource;

/**
 * Редактор объекта. Все изменения остаются локальными: запись в Room плюс
 * постановка в очередь синхронизации (docs/sync.md, раздел 5).
 *
 * <p>Поля зависят от класса: у героя — раса, класс, игрок и котомка; у персонажа —
 * раса и краткое описание; у локации своих полей нет; у предмета — владелец
 * и необязательная ссылка на героя или персонажа (docs/api.md, раздел 6).
 */
public class EntityEditViewModel extends ViewModel {

    /** Всё, что нужно экрану: объект, список участников и указатель имён. */
    public static final class EditState {

        public final EntityRoom entity;
        public final NameIndex nameIndex;
        public final List<EntityRoom> candidates;
        public final List<MemberRoom> members;

        EditState(@NonNull EntityRoom entity, @NonNull NameIndex nameIndex,
                  @NonNull List<EntityRoom> candidates, @NonNull List<MemberRoom> members) {
            this.entity = entity;
            this.nameIndex = nameIndex;
            this.candidates = candidates;
            this.members = members;
        }
    }

    private final EntityRepository entities;
    private final MemberRepository members;
    private final AppExecutors executors;

    private final MutableLiveData<EditState> state = new MutableLiveData<>();
    private final MutableLiveData<Resource<Void>> actionState = new MutableLiveData<>();
    private final MutableLiveData<Boolean> finishRequested = new MutableLiveData<>(false);

    private EntityRoom draft;
    private String campaignId;
    private boolean started;

    public EntityEditViewModel(@NonNull EntityRepository entities, @NonNull MemberRepository members,
                               @NonNull AppExecutors executors) {
        this.entities = entities;
        this.members = members;
        this.executors = executors;
    }

    public void start(@NonNull final String campaignId, @Nullable final String entityId,
                      @Nullable final EntityType kind, final boolean createNew) {
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
                room = entities.createDraft(campaignId, kind);
            }
            if (room.items == null) {
                room.items = new ArrayList<>();
            }
            draft = room;
            List<EntityRoom> all = entities.findAllNow(campaignId);
            List<MemberRoom> memberList = members.findAllNow(campaignId);
            return new EditState(room, NameIndex.of(all), all, memberList);
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

    // --- Поля -----------------------------------------------------------------

    public void setKind(@Nullable EntityType kind) {
        if (draft == null || kind == null) {
            return;
        }
        draft.kind = kind.name();
    }

    public void setName(@Nullable String name) {
        if (draft != null) {
            draft.name = name == null ? "" : name.trim();
        }
    }

    public void setDescription(@Nullable String description) {
        if (draft != null) {
            draft.description = description == null ? "" : description;
        }
    }

    /** Раса: есть у героя и у персонажа. */
    public void setRace(@Nullable String race) {
        if (draft != null) {
            draft.race = nullable(race);
        }
    }

    /** Класс — только у героя. */
    public void setKlass(@Nullable String klass) {
        if (draft != null) {
            draft.klass = nullable(klass);
        }
    }

    /** Кто отыгрывает героя: ссылка на участника кампании. */
    public void setPlayerUserId(@Nullable String playerUserId, @Nullable String playerName) {
        if (draft != null) {
            draft.playerUserId = nullable(playerUserId);
            draft.playerName = nullable(playerName);
        }
    }

    /** Краткое описание — только у персонажа. */
    public void setShortDescription(@Nullable String shortDescription) {
        if (draft != null) {
            draft.shortDescription = nullable(shortDescription);
        }
    }

    /** Владелец предмета «как написано в мире». */
    public void setOwnerName(@Nullable String ownerName) {
        if (draft != null) {
            draft.ownerName = nullable(ownerName);
        }
    }

    /**
     * Ссылка на владельца: не более одной из двух. Имя владельца сохраняем, чтобы
     * карточка сразу показывала человека, не дожидаясь сервера.
     */
    public void setOwnerLink(@Nullable EntityRoom owner) {
        if (draft == null) {
            return;
        }
        if (owner == null) {
            draft.ownerHeroId = null;
            draft.ownerHeroName = null;
            draft.ownerCharacterId = null;
            draft.ownerCharacterName = null;
            return;
        }
        if (EntityType.HERO.name().equalsIgnoreCase(owner.kind)) {
            draft.ownerHeroId = owner.id;
            draft.ownerHeroName = owner.name;
            draft.ownerCharacterId = null;
            draft.ownerCharacterName = null;
        } else {
            draft.ownerCharacterId = owner.id;
            draft.ownerCharacterName = owner.name;
            draft.ownerHeroId = null;
            draft.ownerHeroName = null;
        }
    }

    /** Идентификатор загруженной картинки; {@code null} — фото нет. */
    public void setImageFileId(@Nullable String imageFileId) {
        if (draft != null) {
            draft.imageFileId = nullable(imageFileId);
        }
    }

    @Nullable
    public String imageFileId() {
        return draft == null ? null : draft.imageFileId;
    }

    /** Котомка героя: список заменяется целиком (docs/api.md, раздел 6). */
    public void setItems(@NonNull List<HeroItem> items) {
        if (draft != null) {
            draft.items = new ArrayList<>(items);
        }
    }

    private static String nullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // --- Действия -------------------------------------------------------------

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
