package ru.dndmemory.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.UUID;

import ru.dndmemory.data.remote.dto.AuthResponse;
import ru.dndmemory.data.remote.dto.TokenPairDto;
import ru.dndmemory.data.remote.dto.UserDto;

/**
 * Токены доступа, профиль пользователя и идентификатор установки
 * ({@code clientId} устройства из docs/sync.md, раздел 2).
 *
 * <p>Сессия живёт на телефоне: refresh-токен выдан на 365 дней
 * ({@code platform: "android"}), поэтому логин и пароль вводятся один раз.
 *
 * <p>Хранилище — закрытый файл настроек приложения. Доступ к нему синхронный:
 * это нужно {@link ru.dndmemory.data.remote.AuthInterceptor}, который обязан
 * добавить {@code Authorization} до отправки запроса.
 *
 * <p><b>Спорное место.</b> В ТЗ указан DataStore 1.0.0. Его Java-API
 * ({@code DataStore.updateData}, {@code Flow}) работает только через
 * coroutine-interop ({@code BuildersKt.runBlocking}, {@code FlowKt.first}),
 * который невозможно проверить без компилятора. Поэтому здесь SharedPreferences,
 * а зависимость {@code androidx.datastore:datastore-preferences} объявлена в
 * {@code app/build.gradle} как точка перехода: заменить нужно только этот класс.
 */
public class SessionStore {

    /** Событие отзыва: refresh-токен истёк или недействителен — нужен вход. */
    public static final String SESSION_EXPIRED = "session_expired";

    private static final String FILE = "dnd_session";
    private static final String KEY_ACCESS = "access_token";
    private static final String KEY_REFRESH = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_LOGIN = "login";
    private static final String KEY_DISPLAY_NAME = "display_name";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_HAS_PASSWORD = "has_password";
    private static final String KEY_DEVICE_ID = "device_client_id";
    /** Причина последнего обрыва сессии: переживает перезапуск приложения. */
    private static final String KEY_REVOKE_REASON = "revoke_reason";

    private final SharedPreferences prefs;
    private final MutableLiveData<Boolean> loggedIn = new MutableLiveData<>(false);
    private final MutableLiveData<String> displayName = new MutableLiveData<>();
    /** Признак «сессию прервали»: одноразовое событие для показа экрана входа. */
    private final MutableLiveData<String> revoked = new MutableLiveData<>();

    public SessionStore(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
        ensureDeviceClientId();
        // postValue, а не setValue: контейнер приложения может быть собран
        // и с фонового потока (WorkManager), где setValue запрещён.
        loggedIn.postValue(isLoggedIn());
        displayName.postValue(prefs.getString(KEY_DISPLAY_NAME, null));
    }

    public boolean isLoggedIn() {
        String token = accessToken();
        return token != null && !token.isEmpty();
    }

    public String accessToken() {
        return prefs.getString(KEY_ACCESS, null);
    }

    public String refreshToken() {
        return prefs.getString(KEY_REFRESH, null);
    }

    public String userId() {
        return prefs.getString(KEY_USER_ID, null);
    }

    /** Логин для входа: показывается в профиле только для чтения. */
    public String login() {
        return prefs.getString(KEY_LOGIN, null);
    }

    public String userName() {
        return prefs.getString(KEY_DISPLAY_NAME, null);
    }

    public String email() {
        return prefs.getString(KEY_EMAIL, null);
    }

    public String phone() {
        return prefs.getString(KEY_PHONE, null);
    }

    /** Вход только по паролю, поэтому пароль есть всегда. */
    public boolean hasPassword() {
        return prefs.getBoolean(KEY_HAS_PASSWORD, true);
    }

    /** Идентификатор установки: сервер не отдаёт клиенту его собственные изменения. */
    public String deviceClientId() {
        String id = prefs.getString(KEY_DEVICE_ID, null);
        if (id == null || id.isEmpty()) {
            id = ensureDeviceClientId();
        }
        return id;
    }

    public LiveData<Boolean> loggedIn() {
        return loggedIn;
    }

    public LiveData<String> displayName() {
        return displayName;
    }

    /** Одноразовое событие обрыва сессии: сейчас всегда {@link #SESSION_EXPIRED}. */
    public LiveData<String> revoked() {
        return revoked;
    }

    public void save(AuthResponse response) {
        if (response == null) {
            return;
        }
        SharedPreferences.Editor editor = prefs.edit();
        if (response.accessToken != null) {
            editor.putString(KEY_ACCESS, response.accessToken);
        }
        if (response.refreshToken != null) {
            editor.putString(KEY_REFRESH, response.refreshToken);
        }
        editor.remove(KEY_REVOKE_REASON);
        writeUser(editor, response.user);
        editor.apply();
        publish(response.user == null ? prefs.getString(KEY_DISPLAY_NAME, null)
                : response.user.displayName);
    }

    public void saveTokens(TokenPairDto tokens) {
        if (tokens == null || tokens.accessToken == null) {
            return;
        }
        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_ACCESS, tokens.accessToken);
        if (tokens.refreshToken != null) {
            editor.putString(KEY_REFRESH, tokens.refreshToken);
        }
        editor.apply();
        publish(prefs.getString(KEY_DISPLAY_NAME, null));
    }

    public void saveUser(UserDto user) {
        if (user == null) {
            return;
        }
        SharedPreferences.Editor editor = prefs.edit();
        writeUser(editor, user);
        editor.apply();
        publish(user.displayName);
    }

    /** Полный выход из аккаунта: вместе с сессией уходят и локальные данные. */
    public void clear() {
        String deviceId = deviceClientId();
        // Идентификатор установки сохраняем: он не относится к сессии.
        prefs.edit().clear().putString(KEY_DEVICE_ID, deviceId).apply();
        publish(null);
    }

    /**
     * Сессия прервана (refresh-токен истёк или отозван). Чистим только сессию:
     * кампании, объекты и черновики в Room остаются.
     */
    public void markRevoked(@Nullable String reason) {
        String deviceId = deviceClientId();
        prefs.edit()
                .clear()
                .putString(KEY_DEVICE_ID, deviceId)
                .putString(KEY_REVOKE_REASON, reason == null ? SESSION_EXPIRED : reason)
                .apply();
        publish(null);
        revoked.postValue(reason == null ? SESSION_EXPIRED : reason);
    }

    /**
     * Причина обрыва сессии, если её прервали: экран входа покажет пояснение даже
     * после перезапуска приложения. Флаг одноразовый — после чтения сбрасывается.
     */
    @Nullable
    public String takeRevokeReason() {
        String reason = prefs.getString(KEY_REVOKE_REASON, null);
        if (reason != null) {
            prefs.edit().remove(KEY_REVOKE_REASON).apply();
        }
        return reason;
    }

    /** Событие обработано — экран входа уже показан. */
    public void clearRevokedEvent() {
        revoked.postValue(null);
    }

    private void writeUser(SharedPreferences.Editor editor, @Nullable UserDto user) {
        if (user == null) {
            return;
        }
        editor.putString(KEY_USER_ID, user.id)
                .putString(KEY_LOGIN, user.login)
                .putString(KEY_DISPLAY_NAME, user.displayName)
                .putString(KEY_EMAIL, user.email)
                .putString(KEY_PHONE, user.phone)
                .putBoolean(KEY_HAS_PASSWORD, user.hasPassword);
    }

    /**
     * Публикует состояние сессии. Вызывается из {@code save()}, {@code clear()}
     * и {@code markRevoked()} — то есть с фонового потока, где идут сетевые
     * вызовы. {@code setValue} там запрещён ({@code IllegalStateException:
     * Cannot invoke setValue on a background thread}), поэтому {@code postValue}.
     */
    private void publish(String name) {
        loggedIn.postValue(isLoggedIn());
        displayName.postValue(name);
    }

    private String ensureDeviceClientId() {
        String id = prefs.getString(KEY_DEVICE_ID, null);
        if (id == null || id.isEmpty()) {
            id = "device-" + UUID.randomUUID();
            prefs.edit().putString(KEY_DEVICE_ID, id).apply();
        }
        return id;
    }
}
