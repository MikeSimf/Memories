package ru.dndmemory.data.repo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import ru.dndmemory.data.remote.ApiCall;
import ru.dndmemory.data.remote.ApiService;
import ru.dndmemory.data.remote.dto.CreateDeviceEnrollmentRequest;
import ru.dndmemory.data.remote.dto.DeviceDto;
import ru.dndmemory.data.remote.dto.InviteDto;
import ru.dndmemory.util.AppExecutors;
import ru.dndmemory.util.RepoCallback;

/**
 * Устройства кампании: выпуск QR-кода регистрации, список и отзыв
 * (docs/api.md, раздел 3.1). Требуется право {@code members.manage}.
 */
public class DeviceRepository {

    private final ApiService api;
    private final AppExecutors exec;

    public DeviceRepository(ApiService api, AppExecutors exec) {
        this.api = api;
        this.exec = exec;
    }

    /** QR-код регистрации устройства: имя игрока, роль, привязка к герою, срок. */
    public void createEnrollment(@NonNull String campaignId, @NonNull String displayName,
                                 @NonNull String role, @Nullable String linkedEntityId,
                                 int expiresInHours, int maxUses, @Nullable String label,
                                 RepoCallback<InviteDto> callback) {
        exec.io(() -> ApiCall.execute(api.createDeviceEnrollment(campaignId,
                new CreateDeviceEnrollmentRequest(displayName, role, linkedEntityId,
                        expiresInHours, maxUses, label))), callback);
    }

    public void list(@NonNull String campaignId, RepoCallback<List<DeviceDto>> callback) {
        exec.io(() -> ApiCall.execute(api.devices(campaignId)), callback);
    }

    /** Отзыв: сессии устройства гасятся, участие в кампании сохраняется. */
    public void revoke(@NonNull String campaignId, @NonNull String deviceId,
                       RepoCallback<Void> callback) {
        exec.io(() -> {
            ApiCall.execute(api.revokeDevice(campaignId, deviceId));
            return null;
        }, callback);
    }
}
