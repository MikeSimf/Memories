package ru.dndmemory.ui.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import ru.dndmemory.data.remote.ApiException;
import ru.dndmemory.data.remote.dto.AuthResponse;
import ru.dndmemory.data.remote.dto.ProvidersResponse;
import ru.dndmemory.data.repo.AuthRepository;
import ru.dndmemory.util.RepoCallback;
import ru.dndmemory.util.Resource;

/** Состояние экрана входа: вход, регистрация, список OAuth-провайдеров. */
public class AuthViewModel extends ViewModel {

    private final AuthRepository auth;
    private final MutableLiveData<Resource<String>> result = new MutableLiveData<>();
    private final MutableLiveData<ProvidersResponse> providers = new MutableLiveData<>();

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
    public LiveData<ProvidersResponse> providers() {
        return providers;
    }

    public void login(@NonNull String email, @NonNull String password) {
        result.setValue(Resource.loading());
        auth.login(email, password, new RepoCallback<AuthResponse>() {
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

    public void register(@NonNull String email, @NonNull String password, @NonNull String displayName) {
        result.setValue(Resource.loading());
        auth.register(email, password, displayName, new RepoCallback<AuthResponse>() {
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

    public void loadProviders() {
        auth.providers(new RepoCallback<ProvidersResponse>() {
            @Override
            public void onSuccess(@Nullable ProvidersResponse value) {
                providers.setValue(value);
            }

            @Override
            public void onError(@NonNull ApiException error) {
                // Провайдеры недоступны (офлайн) — кнопки OAuth останутся выключенными.
            }
        });
    }

    @NonNull
    public String authorizeUrl(@NonNull String provider, @NonNull String state) {
        return auth.authorizeUrl(provider, state);
    }

    @NonNull
    public String newState() {
        return auth.newOAuthState();
    }

    @Nullable
    private static String displayName(@Nullable AuthResponse response) {
        if (response == null || response.user == null) {
            return null;
        }
        return response.user.displayName;
    }
}
