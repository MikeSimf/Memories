package ru.dndmemory.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ru.dndmemory.data.remote.ApiException;

/** Состояние асинхронной операции для LiveData. */
public class Resource<T> {

    public enum State {
        LOADING, SUCCESS, ERROR
    }

    @NonNull
    public final State state;

    @Nullable
    public final T data;

    @Nullable
    public final ApiException error;

    private Resource(@NonNull State state, @Nullable T data, @Nullable ApiException error) {
        this.state = state;
        this.data = data;
        this.error = error;
    }

    public static <T> Resource<T> loading() {
        return new Resource<>(State.LOADING, null, null);
    }

    public static <T> Resource<T> success(@Nullable T data) {
        return new Resource<>(State.SUCCESS, data, null);
    }

    public static <T> Resource<T> error(@NonNull ApiException error) {
        return new Resource<>(State.ERROR, null, error);
    }

    public boolean isLoading() {
        return state == State.LOADING;
    }

    public boolean isSuccess() {
        return state == State.SUCCESS;
    }

    public boolean isError() {
        return state == State.ERROR;
    }
}
