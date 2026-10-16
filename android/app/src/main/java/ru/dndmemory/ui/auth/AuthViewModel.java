package ru.dndmemory.ui.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import ru.dndmemory.data.remote.ApiException;
import ru.dndmemory.data.remote.dto.AuthResponse;
import ru.dndmemory.data.repo.AuthRepository;
import ru.dndmemory.util.RepoCallback;
import ru.dndmemory.util.Resource;

/**
 * Состояние экранов входа и сброса пароля: вход по логину и паролю,
 * регистрация (с приглашением или без) и запрос ссылки на сброс пароля.
 */
public class AuthViewModel extends ViewModel {

    private final AuthRepository auth;
    private final MutableLiveData<Resource<String>> result = new MutableLiveData<>();
    private final MutableLiveData<Resource<Void>> resetResult = new MutableLiveData<>();

    public AuthViewModel(@NonNull AuthRepository auth) {
        this.auth = auth;
    }

    @NonNull
    public LiveData<Boolean> loggedIn() {
        return auth.loggedIn();
    }

    @NonNull
    public LiveData<Resource<String>> result() {
        return result;
    }

    @NonNull
    public LiveData<Resource<Void>> resetResult() {
        return resetResult;
    }

    public void login(@NonNull String login, @NonNull String password) {
        result.setValue(Resource.loading());
        auth.login(login, password, new RepoCallback<AuthResponse>() {
            @Override
            public void onSuccess(@Nullable AuthResponse value) {
                result.setValue(Resource.success(displayName(value)));
            }

            @Override
            public void onError(@NonNull ApiException error) {
                result.setValue(Resource.error(error));
            }
        });
    }

    /** Регистрация: логин, почта, пароль, имя; {@code inviteToken} — из QR-кода. */
    public void register(@NonNull String login, @NonNull String email, @NonNull String password,
                         @NonNull String displayName, @Nullable String inviteToken) {
        result.setValue(Resource.loading());
        auth.register(login, email, password, displayName, inviteToken,
                new RepoCallback<AuthResponse>() {
                    @Override
                    public void onSuccess(@Nullable AuthResponse value) {
                        result.setValue(Resource.success(displayName(value)));
                    }

                    @Override
                    public void onError(@NonNull ApiException error) {
                        result.setValue(Resource.error(error));
                    }
                });
    }

    /** Сброс пароля: сервер всегда отвечает {@code 202} — ссылку передают администратору. */
    public void requestPasswordReset(@NonNull String email) {
        resetResult.setValue(Resource.loading());
        auth.requestPasswordReset(email, new RepoCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void value) {
                resetResult.setValue(Resource.success(null));
            }

            @Override
            public void onError(@NonNull ApiException error) {
                resetResult.setValue(Resource.error(error));
            }
        });
    }

    public void clearResult() {
        result.setValue(null);
    }

    public void clearResetResult() {
        resetResult.setValue(null);
    }

    @Nullable
    private static String displayName(@Nullable AuthResponse response) {
        if (response == null || response.user == null) {
            return null;
        }
        return response.user.displayName;
    }
}
