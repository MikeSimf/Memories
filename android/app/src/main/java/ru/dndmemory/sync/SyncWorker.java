package ru.dndmemory.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import ru.dndmemory.App;
import ru.dndmemory.AppContainer;
import ru.dndmemory.data.model.SyncResult;

/**
 * Фоновая синхронизация. Запускается при старте приложения, при появлении сети,
 * после локальной правки (с задержкой) и периодически раз в 15 минут
 * (docs/sync.md, раздел 5).
 */
public class SyncWorker extends Worker {

    public static final String KEY_CAMPAIGN_ID = "campaignId";

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        AppContainer container = App.container(getApplicationContext());
        if (!container.authRepository().isLoggedIn()) {
            // Синхронизировать нечего: пользователь не вошёл.
            return Result.success();
        }
        String campaignId = getInputData().getString(KEY_CAMPAIGN_ID);
        try {
            if (campaignId != null && !campaignId.isEmpty()) {
                SyncResult result = container.syncEngine().sync(campaignId);
                if (result.success) {
                    return Result.success();
                }
                return result.resyncRequired ? Result.retry() : Result.retry();
            }
            container.syncEngine().syncAll();
            return Result.success();
        } catch (Throwable t) {
            // Сеть пропала или сервер недоступен — WorkManager повторит с backoff.
            return Result.retry();
        }
    }
}
