package ru.dndmemory.ui.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

import ru.dndmemory.BuildConfig;
import ru.dndmemory.R;
import ru.dndmemory.data.local.AppDatabase;
import ru.dndmemory.data.local.SettingsStore;
import ru.dndmemory.data.local.entity.CampaignRoom;
import ru.dndmemory.data.model.SyncResult;
import ru.dndmemory.data.remote.ApiClient;
import ru.dndmemory.data.repo.AuthRepository;
import ru.dndmemory.sync.SyncEngine;
import ru.dndmemory.sync.SyncScheduler;
import ru.dndmemory.util.AppExecutors;
import ru.dndmemory.util.DateFormats;
import ru.dndmemory.util.Resource;

/** Настройки: аккаунт, режим синхронизации, полная перезагрузка и выход. */
public class SettingsViewModel extends ViewModel {

    private final AuthRepository auth;
    private final SettingsStore settings;
    private final SyncEngine syncEngine;
    private final SyncScheduler scheduler;
    private final AppDatabase database;
    private final AppExecutors executors;

    private final MutableLiveData<Resource<Void>> actionState = new MutableLiveData<>();
    private final MutableLiveData<Integer> messageRes = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loggedOut = new MutableLiveData<>(false);

    public SettingsViewModel(@NonNull AuthRepository auth, @NonNull SettingsStore settings,
                             @NonNull SyncEngine syncEngine, @NonNull SyncScheduler scheduler,
                             @NonNull AppDatabase database, @NonNull AppExecutors executors) {
        this.auth = auth;
        this.settings = settings;
        this.syncEngine = syncEngine;
        this.scheduler = scheduler;
        this.database = database;
        this.executors = executors;
    }

    @NonNull
    public String userName() {
        String name = auth.displayName().getValue();
        return name == null ? "" : name;
    }

    @NonNull
    public String serverUrl() {
        return ApiClient.baseUrl();
    }

    @NonNull
    public String version() {
        return BuildConfig.VERSION_NAME;
    }

    @NonNull
    public String lastSyncAt() {
        String value = settings.lastSyncAt();
        return value == null ? "" : DateFormats.formatIso(value);
    }

    public boolean isWifiOnly() {
        return settings.isWifiOnly();
    }

    public void setWifiOnly(boolean value) {
        settings.setWifiOnly(value);
        // Меняем ограничение сети у периодического воркера.
        scheduler.schedulePeriodic();
    }

    @NonNull
    public LiveData<Resource<Void>> actionState() {
        return actionState;
    }

    @NonNull
    public LiveData<Integer> messageRes() {
        return messageRes;
    }

    @NonNull
    public LiveData<Boolean> loggedOut() {
        return loggedOut;
    }

    public void syncNow() {
        actionState.setValue(Resource.loading());
        executors.io(() -> {
            syncEngine.syncAll();
            return null;
        }, new ru.dndmemory.util.RepoCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void value) {
                actionState.setValue(Resource.success(null));
                messageRes.setValue(R.string.sync_status_idle);
            }

            @Override
            public void onError(@NonNull ru.dndmemory.data.remote.ApiException error) {
                actionState.setValue(Resource.error(error));
            }
        });
    }

    /** Полная перезагрузка всех кампаний через snapshot (docs/sync.md, раздел 3.3). */
    public void fullResync() {
        actionState.setValue(Resource.loading());
        executors.io(() -> {
            List<CampaignRoom> campaigns = database.campaignDao().findAllNow();
            for (CampaignRoom campaign : campaigns) {
                SyncResult result = syncEngine.forceSnapshot(campaign.id);
                if (!result.success) {
                    throw ru.dndmemory.data.remote.ApiException.unexpected(
                            new IllegalStateException(result.errorMessage == null
                                    ? "resync failed" : result.errorMessage));
                }
            }
            return null;
        }, new ru.dndmemory.util.RepoCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void value) {
                actionState.setValue(Resource.success(null));
                messageRes.setValue(R.string.settings_resync_done);
            }

            @Override
            public void onError(@NonNull ru.dndmemory.data.remote.ApiException error) {
                actionState.setValue(Resource.error(error));
            }
        });
    }

    /** Выход: останавливаем фоновую синхронизацию и чистим локальные данные. */
    public void logout() {
        actionState.setValue(Resource.loading());
        scheduler.cancelPeriodic();
        auth.logout(null);
        executors.io(() -> {
            for (CampaignRoom campaign : database.campaignDao().findAllNow()) {
                database.entityDao().clearForCampaign(campaign.id);
                database.retellingDao().clearForCampaign(campaign.id);
                database.retellingDraftDao().clearForCampaign(campaign.id);
                database.memberDao().clearForCampaign(campaign.id);
                database.pendingChangeDao().clearForCampaign(campaign.id);
                database.syncStateDao().clearForCampaign(campaign.id);
            }
            database.campaignDao().clear();
            return null;
        }, new ru.dndmemory.util.RepoCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void value) {
                actionState.setValue(Resource.success(null));
                loggedOut.setValue(true);
            }

            @Override
            public void onError(@NonNull ru.dndmemory.data.remote.ApiException error) {
                // Локальные данные могли не удалиться — пользователь всё равно выходит.
                loggedOut.setValue(true);
            }
        });
    }

    public void clearActionState() {
        actionState.setValue(null);
    }

    public void clearMessage() {
        messageRes.setValue(null);
    }
}
