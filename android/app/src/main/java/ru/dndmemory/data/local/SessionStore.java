package ru.dndmemory.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.UUID;

import ru.dndmemory.data.remote.dto.AuthResponse;
import ru.dndmemory.data.remote.dto.EnrollResponse;
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
 * <p><b>Два разных «deviceId».</b> {@code KEY_DEVICE_ID} — идентификатор установки
 * для синхронизации, он живёт вместе с приложением. {@code KEY_ENROLLED_DEVICE_ID} —
 * запись устройства на сервере: её видит админ и может отозвать доступ
 * (docs/api.md, раздел 3.1).
 *
 * <p><b>Отзыв доступа.</b> {@link #markRevoked()} чистит только сессию и поднимает
 * {@link #revoked()}: локальные данные кампаний в Room при этом сохраняются, чтобы
 * после нового QR-кода не загружать всё заново.
 *
 * <p><b>Спорное место.</b> В ТЗ указан DataStore 1.0.0. Его Java-API
 * ({@code DataStore.updateData}, {@code Flow}) работает только через
 * coroutine-interop ({@code BuildersKt.runBlocking}, {@code FlowKt.first}),
 * который невозможно проверить без компилятора. Поэтому здесь SharedPreferences,
 * а зависимость {@code androidx.datastore:datastore-preferences} объявлена в
 * {@code app/build.gradle} как точка перехода: заменить нужно только этот класс.
 */
public class SessionStore {

    /** Событие отзыва: админ отозвал устройство — нужен новый QR-код. */
    public static final String REVOKED_DEVICE = "device_revoked";
    /** Событие отзыва: refresh-токен истёк или недействителен — нужен вход. */
    public static final String SESSION_EXPIRED = "session_expired";

    private static final String FILE = "dnd_session";
    private static final String KEY_ACCESS = "access_token";
    private static final String KEY_REFRESH = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_DISPLAY_NAME = "display_name";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_HAS_PASSWORD = "has_password";
    private static final String KEY_DEVICE_ID = "device_client_id";
    private static final String KEY_ENROLLED_DEVICE_ID = "enrolled_device_id";

    private final SharedPreferences prefs;
    private final MutableLiveData<Boolean> loggedIn = new MutableLiveData<>(false);
    private final MutableLiveData<String> displayName = new MutableLiveData<>();
    /** Признак «сессию отозвали»: одноразовое событие для показа экрана QR. */
    private final MutableLiveData<String> revoked = new MutableLiveData<>();

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

    public String phone() {
        return prefs.getString(KEY_PHONE, null);
    }

    /** У аккаунта, созданного по QR устройства, пароля нет — его предлагают задать. */
    public boolean hasPassword() {
        return prefs.getBoolean(KEY_HAS_PASSWORD, true);
    }

    /** Запись устройства на сервере; {@code null} у аккаунта, созданного по паролю. */
    @Nullable
    public String enrolledDeviceId() {
        return prefs.getString(KEY_ENROLLED_DEVICE_ID, null);
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

    /** Одноразовое событие отзыва сессии: {@link #REVOKED_DEVICE} либо {@link #SESSION_EXPIRED}. */
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
        // Вход по паролю/OAuth: сессия больше не привязана к записи устройства.
        editor.remove(KEY_ENROLLED_DEVICE_ID);
        writeUser(editor, response.user);
        editor.apply();
        publish(response.user == null ? prefs.getString(KEY_DISPLAY_NAME, null)
                : response.user.displayName);
    }

    /** Ответ {@code POST /api/devices/enroll}: сессия плюс идентификатор устройства. */
    public void saveEnrollment(EnrollResponse response) {
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
        if (response.deviceId != null) {
            editor.putString(KEY_ENROLLED_DEVICE_ID, response.deviceId);
        }
        writeUser(editor, response.user);
        editor.apply();
        publish(response.user == null ? null : response.user.displayName);
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
     * Доступ отозван (админ отозвал устройство или истёк refresh-токен).
     * Чистим только сессию: кампании, объекты и черновики в Room остаются.
     *
     * @param reason {@link #REVOKED_DEVICE} или {@link #SESSION_EXPIRED}.
     */
    public void markRevoked(@Nullable String reason) {
        String deviceId = deviceClientId();
        prefs.edit()
                .clear()
                .putString(KEY_DEVICE_ID, deviceId)
                .apply();
        publish(null);
        revoked.postValue(reason == null ? SESSION_EXPIRED : reason);
    }

    /** Событие обработано — экран QR уже показан. */
    public void clearRevokedEvent() {
        revoked.postValue(null);
    }

    private void writeUser(SharedPreferences.Editor editor, @Nullable UserDto user) {
        if (user == null) {
            return;
        }
        editor.putString(KEY_USER_ID, user.id)
                .putString(KEY_DISPLAY_NAME, user.displayName)
                .putString(KEY_EMAIL, user.email)
                .putString(KEY_PHONE, user.phone)
                .putBoolean(KEY_HAS_PASSWORD, user.hasPassword);
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
