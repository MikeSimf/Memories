package ru.dndmemory.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ru.dndmemory.data.remote.ApiException;

/** Результат сетевой операции репозитория. Всегда приходит в главном потоке. */
public interface RepoCallback<T> {

    void onSuccess(@Nullable T value);

    void onError(@NonNull ApiException error);
}
