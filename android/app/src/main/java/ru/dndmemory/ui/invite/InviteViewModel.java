package ru.dndmemory.ui.invite;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

import ru.dndmemory.data.remote.ApiException;
import ru.dndmemory.data.remote.dto.InviteDto;
import ru.dndmemory.data.repo.InviteRepository;
import ru.dndmemory.util.RepoCallback;
import ru.dndmemory.util.Resource;

/**
 * Приглашения по QR: выпуск, список, отзыв (docs/api.md, раздел 5).
 * Приглашение всегда одноразовое, поэтому числа использований в запросе нет.
 */
public class InviteViewModel extends ViewModel {

    private final InviteRepository invites;

    private final MutableLiveData<Resource<List<InviteDto>>> listState = new MutableLiveData<>();
    private final MutableLiveData<InviteDto> created = new MutableLiveData<>();
    private final MutableLiveData<Resource<Void>> actionState = new MutableLiveData<>();

    public InviteViewModel(@NonNull InviteRepository invites) {
        this.invites = invites;
    }

    @NonNull
    public MutableLiveData<Resource<List<InviteDto>>> listState() {
        return listState;
    }

    @NonNull
    public MutableLiveData<InviteDto> created() {
        return created;
    }

    @NonNull
    public MutableLiveData<Resource<Void>> actionState() {
        return actionState;
    }

    public void load(@NonNull String campaignId) {
        listState.setValue(Resource.loading());
        invites.list(campaignId, new RepoCallback<List<InviteDto>>() {
            @Override
            public void onSuccess(@Nullable List<InviteDto> value) {
                listState.setValue(Resource.success(value));
            }

            @Override
            public void onError(@NonNull ApiException error) {
                listState.setValue(Resource.error(error));
            }
        });
    }

    public void create(@NonNull String campaignId, @NonNull String role,
                       @Nullable String linkedHeroId, int expiresInHours) {
        actionState.setValue(Resource.loading());
        invites.create(campaignId, role, linkedHeroId, expiresInHours,
                new RepoCallback<InviteDto>() {
                    @Override
                    public void onSuccess(@Nullable InviteDto value) {
                        actionState.setValue(Resource.success(null));
                        created.setValue(value);
                    }

                    @Override
                    public void onError(@NonNull ApiException error) {
                        actionState.setValue(Resource.error(error));
                    }
                });
    }

    public void revoke(@NonNull String campaignId, @NonNull String inviteId) {
        actionState.setValue(Resource.loading());
        invites.revoke(campaignId, inviteId, new RepoCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void value) {
                actionState.setValue(Resource.success(null));
                load(campaignId);
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
