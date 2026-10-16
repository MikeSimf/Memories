package ru.dndmemory.data.repo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import ru.dndmemory.data.local.SessionStore;
import ru.dndmemory.data.remote.ApiCall;
import ru.dndmemory.data.remote.ApiException;
import ru.dndmemory.data.remote.ApiService;
import ru.dndmemory.data.remote.TokenRefresher;
import ru.dndmemory.data.remote.dto.AuthResponse;
import ru.dndmemory.data.remote.dto.LoginRequest;
import ru.dndmemory.data.remote.dto.PasswordResetRequest;
import ru.dndmemory.data.remote.dto.PatchMeRequest;
import ru.dndmemory.data.remote.dto.RefreshRequest;
import ru.dndmemory.data.remote.dto.RegisterRequest;
import ru.dndmemory.data.remote.dto.SetPasswordRequest;
import ru.dndmemory.data.remote.dto.TokenPairDto;
import ru.dndmemory.data.remote.dto.UserDto;
import ru.dndmemory.util.AppExecutors;
import ru.dndmemory.util.RepoCallback;

/**
 * Вход по логину и паролю, регистрация, сброс пароля, профиль и обновление токена.
 *
 * <p>Вход, регистрация, сброс и refresh идут через клиента без {@code Authorization}
 * (иначе повторный 401 зациклится), остальные — через авторизованного:
 * {@link #setApi(ApiService)} вызывается контейнером после сборки OkHttp.
 */
public class AuthRepository implements TokenRefresher {

    /** Приложение просит долгую сессию: refresh-токен на 365 дней вместо 30. */
    public static final String PLATFORM_ANDROID = "android";

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

    /** Логин для входа — в профиле только для чтения. */
    @Nullable
    public String login() {
        return session.login();
    }

    /** Почта для восстановления пароля. */
    @Nullable
    public String email() {
        return session.email();
    }

    /** Телефон в формате, который вернул сервер ({@code +7XXXXXXXXXX}). */
    @Nullable
    public String phone() {
        return session.phone();
    }

    /** Вход только по паролю, поэтому признак всегда {@code true}. */
    public boolean hasPassword() {
        return session.hasPassword();
    }

    /** Идентификатор установки для синхронизации. */
    public String deviceClientId() {
        return session.deviceClientId();
    }

    // --- Вход и регистрация -------------------------------------------------

    /** Вход: логин + пароль, регистр логина не важен (docs/api.md, раздел 3). */
    public void login(String login, String password, RepoCallback<AuthResponse> callback) {
        exec.io(() -> {
            AuthResponse response = ApiCall.execute(
                    authApi.login(new LoginRequest(login, password, PLATFORM_ANDROID)));
            session.save(response);
            return response;
        }, callback);
    }

    /**
     * Регистрация: логин, почта, пароль, имя. С {@code inviteToken} пользователь
     * создаётся и сразу становится участником кампании из приглашения.
     */
    public void register(String login, String email, String password, String displayName,
                         @Nullable String inviteToken, RepoCallback<AuthResponse> callback) {
        exec.io(() -> {
            AuthResponse response = ApiCall.execute(authApi.register(new RegisterRequest(
                    login, email, password, displayName, inviteToken, PLATFORM_ANDROID)));
            session.save(response);
            return response;
        }, callback);
    }

    /**
     * Запрос сброса пароля: сервер всегда отвечает {@code 202}, а ссылку пишет
     * в лог и показывает администратору — SMTP пока не настроен.
     */
    public void requestPasswordReset(String email, RepoCallback<Void> callback) {
        exec.io(() -> {
            ApiCall.execute(authApi.requestPasswordReset(new PasswordResetRequest(email)));
            return null;
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

    /** Профиль: {@code PATCH /api/me} — имя, почта, телефон (docs/api.md, раздел 3). */
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

    /** Смена пароля: текущий обязателен, потому что вход только по паролю. */
    public void changePassword(@Nullable String currentPassword, String newPassword,
                               RepoCallback<Void> callback) {
        exec.io(() -> {
            ApiCall.execute(svc().setPassword(new SetPasswordRequest(currentPassword, newPassword)));
            meQuietly();
            return null;
        }, callback);
    }

    /** Тихая догрузка профиля: нужна, чтобы обновить телефон и признак пароля. */
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
                    // Сессия недействительна: refresh-токен истёк или отозван.
                    // Сетевая ошибка сюда не попадает — офлайн-режим не ломается.
                    session.markRevoked(SessionStore.SESSION_EXPIRED);
                }
                return null;
            }
        }
    }

    @NonNull
    public static String platform() {
        return PLATFORM_ANDROID;
    }
}
