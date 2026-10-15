package ru.dndmemory.ui.invite;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

import ru.dndmemory.data.remote.ApiException;
import ru.dndmemory.data.remote.dto.DeviceDto;
import ru.dndmemory.data.remote.dto.InviteDto;
import ru.dndmemory.data.repo.DeviceRepository;
import ru.dndmemory.data.repo.InviteRepository;
import ru.dndmemory.util.RepoCallback;
import ru.dndmemory.util.Resource;

/**
 * Приглашения по QR и устройства кампании: выпуск, список, отзыв
 * (docs/api.md, разделы 3.1 и 5).
 */
public class InviteViewModel extends ViewModel {

    private final InviteRepository invites;
    private final DeviceRepository devices;

    private final MutableLiveData<Resource<List<InviteDto>>> listState = new MutableLiveData<>();
    private final MutableLiveData<Resource<List<DeviceDto>>> deviceState = new MutableLiveData<>();
    private final MutableLiveData<InviteDto> created = new MutableLiveData<>();
    private final MutableLiveData<Resource<Void>> actionState = new MutableLiveData<>();

    public InviteViewModel(@NonNull InviteRepository invites, @NonNull DeviceRepository devices) {
        this.invites = invites;
        this.devices = devices;
    }

    @NonNull
    public MutableLiveData<Resource<List<InviteDto>>> listState() {
        return listState;
    }

    @NonNull
    public MutableLiveData<Resource<List<DeviceDto>>> deviceState() {
        return deviceState;
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

    /** Список устройств: имя, подпись, платформа, последний вход. */
    public void loadDevices(@NonNull String campaignId) {
        deviceState.setValue(Resource.loading());
        devices.list(campaignId, new RepoCallback<List<DeviceDto>>() {
            @Override
            public void onSuccess(@Nullable List<DeviceDto> value) {
                deviceState.setValue(Resource.success(value));
            }

            @Override
            public void onError(@NonNull ApiException error) {
                deviceState.setValue(Resource.error(error));
            }
        });
    }

    /** Приглашение участнику — обычный режим. */
    public void create(@NonNull String campaignId, @NonNull String role,
                       @Nullable String linkedEntityId, int expiresInHours, int maxUses) {
        actionState.setValue(Resource.loading());
        invites.create(campaignId, role, linkedEntityId, expiresInHours, maxUses,
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

    /** Регистрация устройства: сервер создаст аккаунт игроку при сканировании. */
    public void createEnrollment(@NonNull String campaignId, @NonNull String displayName,
                                 @NonNull String role, @Nullable String linkedEntityId,
                                 int expiresInHours, int maxUses, @Nullable String label) {
        actionState.setValue(Resource.loading());
        devices.createEnrollment(campaignId, displayName, role, linkedEntityId,
                expiresInHours, maxUses, label, new RepoCallback<InviteDto>() {
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

    /** Отзыв устройства: его сессии гасятся, участие в кампании сохраняется. */
    public void revokeDevice(@NonNull String campaignId, @NonNull String deviceId) {
        actionState.setValue(Resource.loading());
        devices.revoke(campaignId, deviceId, new RepoCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void value) {
                actionState.setValue(Resource.success(null));
                loadDevices(campaignId);
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
