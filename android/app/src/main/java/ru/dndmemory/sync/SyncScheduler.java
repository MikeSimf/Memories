package ru.dndmemory.sync;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

import ru.dndmemory.data.local.SettingsStore;

/** Постановка задач синхронизации в WorkManager. */
public class SyncScheduler {

    public static final String UNIQUE_PERIODIC = "dnd-memory-sync-periodic";
    public static final String UNIQUE_IMMEDIATE = "dnd-memory-sync-now";
    public static final String UNIQUE_DEBOUNCED = "dnd-memory-sync-debounced";

    /** Периодичность фонового обновления — 15 минут. */
    public static final long PERIODIC_MINUTES = 15L;

    /** Задержка после локальной правки, чтобы серия изменений ушла одним пакетом. */
    public static final long DEBOUNCE_SECONDS = 3L;

    private final Context context;
    private final SettingsStore settings;

    public SyncScheduler(Context context, SettingsStore settings) {
        this.context = context.getApplicationContext();
        this.settings = settings;
    }

    private Constraints constraints() {
        NetworkType type = settings != null && settings.isWifiOnly()
                ? NetworkType.UNMETERED
                : NetworkType.CONNECTED;
        return new Constraints.Builder()
                .setRequiredNetworkType(type)
                .build();
    }

    private static Data input(@Nullable String campaignId) {
        Data.Builder builder = new Data.Builder();
        if (campaignId != null && !campaignId.isEmpty()) {
            builder.putString(SyncWorker.KEY_CAMPAIGN_ID, campaignId);
        }
        return builder.build();
    }

    /** Периодический воркер раз в 15 минут; повторная постановка не дублируется. */
    public void schedulePeriodic() {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                SyncWorker.class, PERIODIC_MINUTES, TimeUnit.MINUTES)
                .setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    public void cancelPeriodic() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC);
    }

    /** Немедленная синхронизация (старт приложения, кнопка «Синхронизировать»). */
    public void syncNow(@Nullable String campaignId) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(constraints())
                .setInputData(input(campaignId))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_IMMEDIATE, ExistingWorkPolicy.REPLACE, request);
    }

    /** Отложенная синхронизация после локальной правки (debounce 3 секунды). */
    public void syncDebounced(@Nullable String campaignId) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(constraints())
                .setInitialDelay(DEBOUNCE_SECONDS, TimeUnit.SECONDS)
                .setInputData(input(campaignId))
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_DEBOUNCED, ExistingWorkPolicy.REPLACE, request);
    }
}
