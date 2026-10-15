package ru.dndmemory.ui.members;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.data.local.entity.MemberRoom;
import ru.dndmemory.data.model.EntityType;
import ru.dndmemory.data.remote.ApiException;
import ru.dndmemory.data.remote.dto.AddMemberResponse;
import ru.dndmemory.data.repo.EntityRepository;
import ru.dndmemory.data.repo.MemberRepository;
import ru.dndmemory.ui.common.NameIndex;
import ru.dndmemory.util.AppExecutors;
import ru.dndmemory.util.RepoCallback;
import ru.dndmemory.util.Resource;

/**
 * Участники кампании: роли и привязка к герою (docs/roles.md).
 * Управлять ими может только {@code OWNER}.
 */
public class MembersViewModel extends ViewModel {

    private final MemberRepository members;
    private final EntityRepository entities;

    private final MediatorLiveData<List<MemberRoom>> items = new MediatorLiveData<>();
    private final MutableLiveData<List<EntityRoom>> heroes = new MutableLiveData<>();
    private final MutableLiveData<NameIndex> nameIndex = new MutableLiveData<>();
    private final MutableLiveData<Resource<Void>> actionState = new MutableLiveData<>();
    private final MutableLiveData<Integer> messageRes = new MutableLiveData<>();

    private String campaignId;
    private boolean initialized;

    public MembersViewModel(@NonNull MemberRepository members, @NonNull EntityRepository entities,
                            @NonNull AppExecutors executors) {
        this.members = members;
        this.entities = entities;
    }

    public void init(@NonNull String campaignId) {
        if (initialized) {
            return;
        }
        initialized = true;
        this.campaignId = campaignId;
        items.addSource(members.observe(campaignId), items::setValue);
        loadHeroes();
    }

    @NonNull
    public LiveData<List<MemberRoom>> items() {
        return items;
    }

    @NonNull
    public LiveData<List<EntityRoom>> heroes() {
        return heroes;
    }

    @NonNull
    public LiveData<NameIndex> nameIndex() {
        return nameIndex;
    }

    @NonNull
    public LiveData<Resource<Void>> actionState() {
        return actionState;
    }

    /** Сообщение об успехе: добавлен участник или создано приглашение. */
    @NonNull
    public LiveData<Integer> messageRes() {
        return messageRes;
    }

    private void loadHeroes() {
        entities.loadEntities(campaignId, new RepoCallback<List<EntityRoom>>() {
            @Override
            public void onSuccess(@Nullable List<EntityRoom> value) {
                List<EntityRoom> result = new ArrayList<>();
                if (value != null) {
                    for (EntityRoom entity : value) {
                        if (EntityType.HERO.name().equalsIgnoreCase(entity.type)) {
                            result.add(entity);
                        }
                    }
                }
                heroes.setValue(result);
                nameIndex.setValue(NameIndex.of(value));
            }

            @Override
            public void onError(@NonNull ApiException error) {
                nameIndex.setValue(NameIndex.of(null));
            }
        });
    }

    public void refresh() {
        members.refresh(campaignId, null);
        loadHeroes();
    }

    public void add(@NonNull String email, @NonNull String role, @Nullable String linkedEntityId) {
        actionState.setValue(Resource.loading());
        members.add(campaignId, email, role, linkedEntityId, new RepoCallback<AddMemberResponse>() {
            @Override
            public void onSuccess(@Nullable AddMemberResponse value) {
                actionState.setValue(Resource.success(null));
                boolean invited = value != null && value.member == null && value.invite != null;
                messageRes.setValue(invited
                        ? ru.dndmemory.R.string.members_invited
                        : ru.dndmemory.R.string.members_added);
            }

            @Override
            public void onError(@NonNull ApiException error) {
                actionState.setValue(Resource.error(error));
            }
        });
    }

    public void changeRole(@NonNull String memberId, @NonNull String role,
                           @Nullable String linkedEntityId) {
        actionState.setValue(Resource.loading());
        members.changeRole(campaignId, memberId, role, linkedEntityId, new RepoCallback<MemberRoom>() {
            @Override
            public void onSuccess(@Nullable MemberRoom value) {
                actionState.setValue(Resource.success(null));
            }

            @Override
            public void onError(@NonNull ApiException error) {
                actionState.setValue(Resource.error(error));
            }
        });
    }

    public void remove(@NonNull String memberId) {
        actionState.setValue(Resource.loading());
        members.remove(campaignId, memberId, new RepoCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void value) {
                actionState.setValue(Resource.success(null));
            }

            @Override
            public void onError(@NonNull ApiException error) {
                actionState.setValue(Resource.error(error));
            }
        });
    }

    @NonNull
    public String campaignId() {
        return campaignId;
    }

    public void clearActionState() {
        actionState.setValue(null);
    }

    public void clearMessage() {
        messageRes.setValue(null);
    }
}
