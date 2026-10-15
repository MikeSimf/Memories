package ru.dndmemory;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;

import ru.dndmemory.data.remote.ApiException;
import ru.dndmemory.data.remote.dto.UserDto;
import ru.dndmemory.util.AppExecutors;
import ru.dndmemory.util.RepoCallback;

/**
 * Точка входа приложения.
 *
 * <p>При старте: собирает {@link AppContainer}, запускает синхронизацию
 * ({@code SyncWorker} при {@code onStart}, docs/sync.md раздел 5) и ставит
 * периодический воркер на 15 минут.
 */
public class App extends Application {

    private static volatile AppContainer container;

    /** Контейнер для Activity/Fragment/Worker. */
    @NonNull
    public static AppContainer container(@NonNull Context context) {
        AppContainer local = container;
        if (local == null) {
            synchronized (App.class) {
                local = container;
                if (local == null) {
                    local = new AppContainer(context.getApplicationContext());
                    container = local;
                }
            }
        }
        return local;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        final AppContainer appContainer = container(this);

        AppExecutors.get().io(() -> {
            if (!appContainer.authRepository().isLoggedIn()) {
                return;
            }
            appContainer.authRepository().loadMe(new RepoCallback<UserDto>() {
                @Override
                public void onSuccess(UserDto value) {
                    // профиль обновлён в SessionStore
                }

                @Override
                public void onError(ApiException error) {
                    // офлайн — имя остаётся прежним
                }
            });
            appContainer.syncEngine().syncAll();
        });

        appContainer.syncScheduler().schedulePeriodic();
    }
}
