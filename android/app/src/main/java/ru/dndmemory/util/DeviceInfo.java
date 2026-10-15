package ru.dndmemory.util;

import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;

/** Подпись устройства для сервера: модель телефона (docs/api.md, раздел 3.1). */
public final class DeviceInfo {

    private DeviceInfo() {
    }

    /** Например {@code Xiaomi M2101K6G}. */
    @NonNull
    public static String label() {
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
        String model = Build.MODEL == null ? "" : Build.MODEL.trim();
        String label = (manufacturer + " " + model).trim();
        if (TextUtils.isEmpty(label)) {
            return "Android";
        }
        return label.length() > 120 ? label.substring(0, 120) : label;
    }

    /** Платформа в терминах контракта: сервер ждёт {@code android}. */
    @NonNull
    public static String platform() {
        return "android";
    }
}
