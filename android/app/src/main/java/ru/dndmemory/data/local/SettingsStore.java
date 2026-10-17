package ru.dndmemory.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/** Пользовательские настройки: синхронизация только по Wi-Fi, время последней синхронизации. */
public class SettingsStore {

    private static final String FILE = "dnd_settings";
    private static final String KEY_WIFI_ONLY = "sync_wifi_only";
    private static final String KEY_LAST_SYNC = "last_sync_at";

    private final SharedPreferences prefs;
    private final MutableLiveData<Boolean> wifiOnly = new MutableLiveData<>(false);
    private final MutableLiveData<String> lastSyncAt = new MutableLiveData<>();

    public SettingsStore(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
        wifiOnly.postValue(prefs.getBoolean(KEY_WIFI_ONLY, false));
        lastSyncAt.postValue(prefs.getString(KEY_LAST_SYNC, null));
    }

    public boolean isWifiOnly() {
        return prefs.getBoolean(KEY_WIFI_ONLY, false);
    }

    public LiveData<Boolean> wifiOnly() {
        return wifiOnly;
    }

    public void setWifiOnly(boolean value) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply();
        // вызывается и из синхронизации в фоне — setValue там запрещён
        wifiOnly.postValue(value);
    }

    public String lastSyncAt() {
        return prefs.getString(KEY_LAST_SYNC, null);
    }

    public LiveData<String> observeLastSyncAt() {
        return lastSyncAt;
    }

    public void setLastSyncAt(String iso) {
        prefs.edit().putString(KEY_LAST_SYNC, iso).apply();
        // SyncEngine пишет время из фонового потока
        lastSyncAt.postValue(iso);
    }
}
