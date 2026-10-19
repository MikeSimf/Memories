package ru.dndmemory;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import okhttp3.OkHttpClient;
import ru.dndmemory.data.local.AppDatabase;
import ru.dndmemory.data.local.SessionStore;
import ru.dndmemory.data.local.SettingsStore;
import ru.dndmemory.data.local.entity.CampaignRoom;
import ru.dndmemory.data.remote.ApiClient;
import ru.dndmemory.data.remote.ApiService;
import ru.dndmemory.data.remote.AuthInterceptor;
import ru.dndmemory.data.remote.TokenAuthenticator;
import ru.dndmemory.data.repo.AuthRepository;
import ru.dndmemory.data.repo.CampaignRepository;
import ru.dndmemory.data.repo.EntityRepository;
import ru.dndmemory.data.repo.FileRepository;
import ru.dndmemory.data.repo.InviteRepository;
import ru.dndmemory.data.repo.MaterialRepository;
import ru.dndmemory.data.repo.MemberRepository;
import ru.dndmemory.data.repo.PendingWriter;
import ru.dndmemory.data.repo.RaceRepository;
import ru.dndmemory.data.repo.UpdateRepository;
import ru.dndmemory.data.repo.RetellingRepository;
import ru.dndmemory.data.remote.ApiException;
import ru.dndmemory.util.RepoCallback;
import ru.dndmemory.sync.SyncEngine;
import ru.dndmemory.sync.SyncScheduler;
import ru.dndmemory.util.AppExecutors;

/**
 * Ручной DI-контейнер (без Hilt/Dagger). Собирает базу, сеть, репозитории и
 * движок синхронизации; создаётся один раз в {@link App#onCreate()}.
 *
 * <p>Порядок сборки важен: сначала клиент без авторизации (нужен для refresh),
 * потом {@link AuthRepository} как {@link ru.dndmemory.data.remote.TokenRefresher},
 * и только затем основной OkHttp с перехватчиком 401.
 */
public class AppContainer {

    private final AppDatabase database;
    private final SessionStore sessionStore;
    private final SettingsStore settingsStore;
    private final ApiService apiService;
    private final AuthRepository authRepository;
    private final CampaignRepository campaignRepository;
    private final EntityRepository entityRepository;
    private final RetellingRepository retellingRepository;
    private final MemberRepository memberRepository;
    private final InviteRepository inviteRepository;
    private final FileRepository fileRepository;
    private final RaceRepository raceRepository;
    private final UpdateRepository updateRepository;
    private final MaterialRepository materialRepository;
    private final OkHttpClient authorizedClient;
    private final SyncEngine syncEngine;
    private final SyncScheduler syncScheduler;

    public AppContainer(Context context) {
        Context app = context.getApplicationContext();
        AppExecutors executors = AppExecutors.get();

        database = AppDatabase.create(app);
        sessionStore = new SessionStore(app);
        settingsStore = new SettingsStore(app);

        // 1. Клиент без авторизации: вход, регистрация, сброс пароля, refresh.
        OkHttpClient plainClient = ApiClient.plainHttpClient();
        ApiService plainApi = ApiClient.create(plainClient);

        // 2. Refresh-логика живёт в AuthRepository и не зависит от основного клиента.
        authRepository = new AuthRepository(plainApi, sessionStore, executors);

        // 3. Основной клиент: Bearer + обновление по 401.
        TokenAuthenticator authenticator = new TokenAuthenticator(sessionStore, authRepository);
        AuthInterceptor interceptor = new AuthInterceptor(sessionStore);
        authorizedClient =
                ApiClient.authorizedHttpClient(plainClient, authenticator, interceptor);
        apiService = ApiClient.create(authorizedClient);
        authRepository.setApi(apiService);

        // 4. Репозитории.
        PendingWriter pendingWriter = new PendingWriter(database);
        campaignRepository = new CampaignRepository(database, apiService, pendingWriter, executors);
        entityRepository = new EntityRepository(database, apiService, pendingWriter, executors);
        retellingRepository = new RetellingRepository(database, apiService, pendingWriter, executors);
        memberRepository = new MemberRepository(database, apiService, executors);
        inviteRepository = new InviteRepository(apiService, executors);
        fileRepository = new FileRepository(apiService, executors);
        raceRepository = new RaceRepository(apiService, executors);
        updateRepository = new UpdateRepository(apiService, executors);
        materialRepository = new MaterialRepository(database, apiService, pendingWriter, executors);

        // 5. Синхронизация.
        syncEngine = new SyncEngine(database, apiService, sessionStore.deviceClientId());
        syncScheduler = new SyncScheduler(app, settingsStore);
    }

    /**
     * Выход из аккаунта: останавливает периодическую синхронизацию, чистит сессию
     * и локальные данные кампаний. {@code onDone} вызывается в главном потоке.
     *
     * <p>Раньше выход был размазан по двум экранам, и из списка кампаний
     * локальная база не чистилась вовсе.
     */
    public void logout(@Nullable final Runnable onDone) {
        syncScheduler.cancelPeriodic();
        authRepository.logout(null);
        AppExecutors.get().io(() -> {
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
        }, new RepoCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void value) {
                if (onDone != null) {
                    onDone.run();
                }
            }

            @Override
            public void onError(@NonNull ApiException error) {
                // Локальные данные могли не удалиться — пользователь всё равно выходит.
                if (onDone != null) {
                    onDone.run();
                }
            }
        });
    }

    public AppDatabase database() {
        return database;
    }

    public SessionStore sessionStore() {
        return sessionStore;
    }

    public SettingsStore settingsStore() {
        return settingsStore;
    }

    public ApiService apiService() {
        return apiService;
    }

    public AuthRepository authRepository() {
        return authRepository;
    }

    public CampaignRepository campaignRepository() {
        return campaignRepository;
    }

    public EntityRepository entityRepository() {
        return entityRepository;
    }

    public RetellingRepository retellingRepository() {
        return retellingRepository;
    }

    public MemberRepository memberRepository() {
        return memberRepository;
    }

    public InviteRepository inviteRepository() {
        return inviteRepository;
    }

    public MaterialRepository materialRepository() {
        return materialRepository;
    }

    public UpdateRepository updateRepository() {
        return updateRepository;
    }

    public RaceRepository raceRepository() {
        return raceRepository;
    }

    public FileRepository fileRepository() {
        return fileRepository;
    }

    /** Авторизованный клиент: нужен загрузчику картинок для GET /api/files/{id}. */
    public OkHttpClient okHttpClient() {
        return authorizedClient;
    }

    public SyncEngine syncEngine() {
        return syncEngine;
    }

    public SyncScheduler syncScheduler() {
        return syncScheduler;
    }
}
