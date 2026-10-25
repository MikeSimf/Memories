package ru.dndmemory;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import ru.dndmemory.data.remote.ApiException;
import ru.dndmemory.data.remote.dto.UserDto;
import ru.dndmemory.ui.common.Nav;
import ru.dndmemory.util.AppExecutors;
import ru.dndmemory.util.RepoCallback;

/**
 * Точка входа приложения.
 *
 * <p>При старте: собирает {@link AppContainer}, запускает синхронизацию
 * ({@code SyncWorker} при {@code onStart}, docs/sync.md раздел 6) и ставит
 * периодический воркер на 15 минут.
 *
 * <p>Следит за обрывом сессии: если сервер отверг refresh-токен, приложение
 * показывает экран входа с пояснением. Локальные данные при этом не удаляются.
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
        ru.dndmemory.util.CrashReporter.install(this);
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
        observeRevoked(appContainer);
    }

    /**
     * Сессия прервана: она уже очищена, локальные данные на месте. Открываем
     * экран входа с пояснением.
     */
    private void observeRevoked(@NonNull final AppContainer appContainer) {
        appContainer.sessionStore().revoked().observeForever(reason -> {
            if (reason == null) {
                return;
            }
            final String message = reason;
            // Событие приходит из фонового потока — экран открываем в главном.
            new Handler(Looper.getMainLooper()).post(() -> {
                Intent intent = Nav.auth(this, null, message);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                try {
                    startActivity(intent);
                } catch (RuntimeException ignored) {
                    // Фоновый запуск мог быть запрещён системой: при следующем
                    // открытии приложения Splash сам покажет экран входа.
                }
                appContainer.sessionStore().clearRevokedEvent();
                // Одноразовый флаг причины: повторно не показываем.
                appContainer.sessionStore().takeRevokeReason();
            });
        });
    }
}
