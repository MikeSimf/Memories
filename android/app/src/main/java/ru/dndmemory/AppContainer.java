package ru.dndmemory;

import android.content.Context;

import okhttp3.OkHttpClient;
import ru.dndmemory.data.local.AppDatabase;
import ru.dndmemory.data.local.SessionStore;
import ru.dndmemory.data.local.SettingsStore;
import ru.dndmemory.data.remote.ApiClient;
import ru.dndmemory.data.remote.ApiService;
import ru.dndmemory.data.remote.AuthInterceptor;
import ru.dndmemory.data.remote.TokenAuthenticator;
import ru.dndmemory.data.repo.AuthRepository;
import ru.dndmemory.data.repo.CampaignRepository;
import ru.dndmemory.data.repo.DeviceRepository;
import ru.dndmemory.data.repo.EntityRepository;
import ru.dndmemory.data.repo.InviteRepository;
import ru.dndmemory.data.repo.MemberRepository;
import ru.dndmemory.data.repo.PendingWriter;
import ru.dndmemory.data.repo.RetellingRepository;
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
    private final DeviceRepository deviceRepository;
    private final SyncEngine syncEngine;
    private final SyncScheduler syncScheduler;

    public AppContainer(Context context) {
        Context app = context.getApplicationContext();
        AppExecutors executors = AppExecutors.get();

        database = AppDatabase.create(app);
        sessionStore = new SessionStore(app);
        settingsStore = new SettingsStore(app);

        // 1. Клиент без авторизации: вход, регистрация, refresh, провайдеры.
        OkHttpClient plainClient = ApiClient.plainHttpClient();
        ApiService plainApi = ApiClient.create(plainClient);

        // 2. Refresh-логика живёт в AuthRepository и не зависит от основного клиента.
        authRepository = new AuthRepository(plainApi, sessionStore, executors);

        // 3. Основной клиент: Bearer + обновление по 401.
        TokenAuthenticator authenticator = new TokenAuthenticator(sessionStore, authRepository);
        AuthInterceptor interceptor = new AuthInterceptor(sessionStore);
        OkHttpClient authorizedClient =
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
        deviceRepository = new DeviceRepository(apiService, executors);

        // 5. Синхронизация.
        syncEngine = new SyncEngine(database, apiService, sessionStore.deviceClientId());
        syncScheduler = new SyncScheduler(app, settingsStore);
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

    public DeviceRepository deviceRepository() {
        return deviceRepository;
    }

    public SyncEngine syncEngine() {
        return syncEngine;
    }

    public SyncScheduler syncScheduler() {
        return syncScheduler;
    }
}
