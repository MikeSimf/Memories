package ru.dndmemory.data.repo;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import ru.dndmemory.BuildConfig;
import ru.dndmemory.data.local.SessionStore;
import ru.dndmemory.data.remote.ApiCall;
import ru.dndmemory.data.remote.ApiClient;
import ru.dndmemory.data.remote.ApiException;
import ru.dndmemory.data.remote.ApiService;
import ru.dndmemory.data.remote.TokenRefresher;
import ru.dndmemory.data.remote.dto.AuthResponse;
import ru.dndmemory.data.remote.dto.EnrollRequest;
import ru.dndmemory.data.remote.dto.EnrollResponse;
import ru.dndmemory.data.remote.dto.LoginRequest;
import ru.dndmemory.data.remote.dto.OAuthCallbackRequest;
import ru.dndmemory.data.remote.dto.PatchMeRequest;
import ru.dndmemory.data.remote.dto.ProvidersResponse;
import ru.dndmemory.data.remote.dto.RefreshRequest;
import ru.dndmemory.data.remote.dto.RegisterRequest;
import ru.dndmemory.data.remote.dto.SetPasswordRequest;
import ru.dndmemory.data.remote.dto.TokenPairDto;
import ru.dndmemory.data.remote.dto.UserDto;
import ru.dndmemory.util.AppExecutors;
import ru.dndmemory.util.RepoCallback;

/**
 * Вход, регистрация, OAuth, профиль и обновление токена.
 *
 * <p>Запросы входа и refresh идут через клиента без {@code Authorization}
 * (иначе повторный 401 зациклится), остальные — через авторизованного:
 * {@link #setApi(ApiService)} вызывается контейнером после сборки OkHttp.
 */
public class AuthRepository implements TokenRefresher {

    /** Разобранный ответ {@code dndmemory://oauth?code=…&state=…}. */
    public static class OAuthRedirect {
        public final String code;
        public final String state;
        public final String error;

        public OAuthRedirect(@Nullable String code, @Nullable String state, @Nullable String error) {
            this.code = code;
            this.state = state;
            this.error = error;
        }

        public boolean isSuccess() {
            return code != null && !code.isEmpty() && (error == null || error.isEmpty());
        }
    }

    private final ApiService authApi;
    private final SessionStore session;
    private final AppExecutors exec;
    private final Object refreshLock = new Object();

    private volatile ApiService api;

    public AuthRepository(ApiService authApi, SessionStore session, AppExecutors exec) {
        this.authApi = authApi;
        this.session = session;
        this.exec = exec;
    }

    /** Авторизованный клиент появляется после сборки основного Retrofit. */
    public void setApi(ApiService api) {
        this.api = api;
    }

    private ApiService svc() {
        ApiService current = api;
        return current != null ? current : authApi;
    }

    public LiveData<Boolean> loggedIn() {
        return session.loggedIn();
    }

    public LiveData<String> displayName() {
        return session.displayName();
    }

    public boolean isLoggedIn() {
        return session.isLoggedIn();
    }

    public String userId() {
        return session.userId();
    }

    /** Идентификатор установки для синхронизации. */
    public String deviceClientId() {
        return session.deviceClientId();
    }

    // --- Вход ---------------------------------------------------------------

    public void login(String email, String password, RepoCallback<AuthResponse> callback) {
        exec.io(() -> {
            AuthResponse response = ApiCall.execute(authApi.login(new LoginRequest(email, password)));
            session.save(response);
            return response;
        }, callback);
    }

    public void register(String email, String password, String displayName,
                         RepoCallback<AuthResponse> callback) {
        exec.io(() -> {
            AuthResponse response = ApiCall.execute(
                    authApi.register(new RegisterRequest(email, password, displayName)));
            session.save(response);
            return response;
        }, callback);
    }

    public void logout(RepoCallback<Void> callback) {
        exec.io(() -> {
            String refresh = session.refreshToken();
            try {
                if (refresh != null && !refresh.isEmpty()) {
                    ApiCall.execute(authApi.logout(new RefreshRequest(refresh)));
                }
            } catch (ApiException ignored) {
                // Локальный выход важнее ответа сервера.
            }
            session.clear();
            return null;
        }, callback);
    }

    public void loadMe(RepoCallback<UserDto> callback) {
        exec.io(() -> {
            UserDto user = ApiCall.execute(svc().me());
            session.saveUser(user);
            return user;
        }, callback);
    }

    /** Профиль и контакты для входа: {@code PATCH /api/me} (docs/api.md, раздел 3). */
    public void updateProfile(String displayName, @Nullable String avatarFileId,
                              @Nullable String email, @Nullable String phone,
                              RepoCallback<UserDto> callback) {
        exec.io(() -> {
            UserDto user = ApiCall.execute(svc().patchMe(
                    new PatchMeRequest(displayName, avatarFileId, email, phone)));
            session.saveUser(user);
            return user;
        }, callback);
    }

    /**
     * Задать или сменить пароль. {@code currentPassword} нужен только при
     * {@code hasPassword: true} — иначе сервер ответит ошибкой.
     */
    public void changePassword(@Nullable String currentPassword, String newPassword,
                               RepoCallback<Void> callback) {
        exec.io(() -> {
            ApiCall.execute(svc().setPassword(new SetPasswordRequest(currentPassword, newPassword)));
            // Пароль задан: обновляем профиль, чтобы признак hasPassword стал true.
            meQuietly();
            return null;
        }, callback);
    }

    /** Тихая догрузка профиля: нужна, чтобы обновить {@code hasPassword} и телефон. */
    @Nullable
    private UserDto meQuietly() {
        try {
            UserDto user = ApiCall.execute(svc().me());
            session.saveUser(user);
            return user;
        } catch (ApiException e) {
            return null;
        }
    }

    // --- Регистрация устройства по QR (docs/api.md, раздел 3.1) -------------

    /**
     * Обмен токена из QR мастера на сессию. Запрос идёт через клиента без
     * {@code Authorization}: это единственный способ войти без пароля.
     */
    public void enrollDevice(String token, String label, RepoCallback<EnrollResponse> callback) {
        exec.io(() -> {
            EnrollResponse response = ApiCall.execute(
                    authApi.enrollDevice(new EnrollRequest(token, label, "android")));
            session.saveEnrollment(response);
            return response;
        }, callback);
    }

    // --- OAuth --------------------------------------------------------------

    public void providers(RepoCallback<ProvidersResponse> callback) {
        exec.io(() -> ApiCall.execute(authApi.providers()), callback);
    }

    /** Полный адрес страницы авторизации провайдера для Custom Tabs. */
    public String authorizeUrl(String provider, String state) {
        return ApiClient.baseUrl() + "api/auth/oauth/" + provider + "/authorize"
                + "?redirectUri=" + encode(BuildConfig.OAUTH_REDIRECT_URI)
                + "&state=" + encode(state);
    }

    public String newOAuthState() {
        return ru.dndmemory.util.Ids.uuid();
    }

    public void oauthCallback(String provider, String code, String state,
                              RepoCallback<AuthResponse> callback) {
        exec.io(() -> {
            AuthResponse response = ApiCall.execute(authApi.oauthCallback(provider,
                    new OAuthCallbackRequest(code, BuildConfig.OAUTH_REDIRECT_URI, state)));
            session.save(response);
            return response;
        }, callback);
    }

    /** Разбор {@code dndmemory://oauth?code=…&state=…}. */
    @Nullable
    public OAuthRedirect parseRedirect(@Nullable Uri uri) {
        if (uri == null) {
            return null;
        }
        String code = uri.getQueryParameter("code");
        String state = uri.getQueryParameter("state");
        String error = uri.getQueryParameter("error");
        if ((code == null || code.isEmpty()) && (error == null || error.isEmpty())) {
            return null;
        }
        return new OAuthRedirect(code, state, error);
    }

    // --- TokenRefresher -----------------------------------------------------

    /**
     * Обновление access-токена. Refresh-токен ротируется, новый сохраняем.
     * При сетевой ошибке сессию НЕ сбрасываем — иначе офлайн-режим ломался бы.
     */
    @Override
    public String refreshBlocking() {
        synchronized (refreshLock) {
            String refresh = session.refreshToken();
            if (refresh == null || refresh.isEmpty()) {
                return null;
            }
            try {
                TokenPairDto tokens = ApiCall.execute(authApi.refresh(new RefreshRequest(refresh)));
                if (tokens == null || tokens.accessToken == null) {
                    return null;
                }
                session.saveTokens(tokens);
                return tokens.accessToken;
            } catch (ApiException e) {
                if (e.isUnauthorized() || e.isForbidden() || e.isNotFound()) {
                    // Сессия недействительна: устройство отозвали или refresh-токен истёк.
                    // Сетевая ошибка сюда не попадает — офлайн-режим не ломается.
                    session.markRevoked(revokedReason(e));
                }
                return null;
            }
        }
    }

    /** Причина для экрана QR: у отозванного устройства сервер отвечает особо. */
    @NonNull
    private static String revokedReason(@Nullable ApiException e) {
        String message = e == null ? null : e.getServerMessage();
        if (message != null && message.toLowerCase(java.util.Locale.ROOT).contains("отозван")) {
            return SessionStore.REVOKED_DEVICE;
        }
        return SessionStore.SESSION_EXPIRED;
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value == null ? "" : value;
        }
    }
}
