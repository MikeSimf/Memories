package ru.dndmemory.data.local;

import android.content.Context;
import android.content.SharedPreferences;

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

    private static final String FILE = "dnd_session";
    private static final String KEY_ACCESS = "access_token";
    private static final String KEY_REFRESH = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_DISPLAY_NAME = "display_name";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_DEVICE_ID = "device_client_id";

    private final SharedPreferences prefs;
    private final MutableLiveData<Boolean> loggedIn = new MutableLiveData<>(false);
    private final MutableLiveData<String> displayName = new MutableLiveData<>();

    public SessionStore(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
        ensureDeviceClientId();
        loggedIn.setValue(isLoggedIn());
        displayName.setValue(prefs.getString(KEY_DISPLAY_NAME, null));
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

    public String userName() {
        return prefs.getString(KEY_DISPLAY_NAME, null);
    }

    public String email() {
        return prefs.getString(KEY_EMAIL, null);
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
        if (response.user != null) {
            editor.putString(KEY_USER_ID, response.user.id);
            editor.putString(KEY_DISPLAY_NAME, response.user.displayName);
            editor.putString(KEY_EMAIL, response.user.email);
        }
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
        prefs.edit()
                .putString(KEY_USER_ID, user.id)
                .putString(KEY_DISPLAY_NAME, user.displayName)
                .putString(KEY_EMAIL, user.email)
                .apply();
        publish(user.displayName);
    }

    public void clear() {
        String deviceId = deviceClientId();
        // Идентификатор установки сохраняем: он не относится к сессии.
        prefs.edit().clear().putString(KEY_DEVICE_ID, deviceId).apply();
        publish(null);
    }

    private void publish(String name) {
        loggedIn.setValue(isLoggedIn());
        displayName.setValue(name);
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
