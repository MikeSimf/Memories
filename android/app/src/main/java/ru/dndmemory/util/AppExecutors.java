package ru.dndmemory.util;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.dndmemory.data.remote.ApiException;

/**
 * Фоновые потоки и главный поток. Сетевые вызовы выполняются синхронно
 * ({@code Call.execute()}) в пуле, результат отдаётся в главный поток.
 */
public class AppExecutors {

    private static volatile AppExecutors instance;

    private final ExecutorService diskAndNetwork = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static AppExecutors get() {
        AppExecutors local = instance;
        if (local == null) {
            synchronized (AppExecutors.class) {
                local = instance;
                if (local == null) {
                    local = new AppExecutors();
                    instance = local;
                }
            }
        }
        return local;
    }

    public ExecutorService io() {
        return diskAndNetwork;
    }

    public void io(@NonNull Runnable task) {
        diskAndNetwork.execute(task);
    }

    public void main(@NonNull Runnable task) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run();
        } else {
            mainHandler.post(task);
        }
    }

    /** Выполняет задачу в фоне и отдаёт результат (или ошибку) в главный поток. */
    public <T> void io(@NonNull final Callable<T> task, @Nullable final RepoCallback<T> callback) {
        diskAndNetwork.execute(() -> {
            try {
                final T value = task.call();
                main(() -> {
                    if (callback != null) {
                        callback.onSuccess(value);
                    }
                });
            } catch (final ApiException e) {
                main(() -> {
                    if (callback != null) {
                        callback.onError(e);
                    }
                });
            } catch (final Throwable t) {
                final ApiException wrapped = ApiException.unexpected(t);
                main(() -> {
                    if (callback != null) {
                        callback.onError(wrapped);
                    }
                });
            }
        });
    }

    /** Вариант для задач без промежуточного значения на выходе. */
    public void io(@NonNull final Runnable task, @Nullable final RepoCallback<Void> callback) {
        io(() -> {
            task.run();
            return null;
        }, callback);
    }
}
