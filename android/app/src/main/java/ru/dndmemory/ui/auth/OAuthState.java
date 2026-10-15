package ru.dndmemory.ui.auth;

import androidx.annotation.Nullable;

/**
 * Одноразовое значение {@code state} для OAuth: защищает от подмены ответа.
 * Живёт в памяти процесса — если система убила процесс во время входа,
 * приложение попросит повторить попытку.
 */
public final class OAuthState {

    private static String pendingProvider;
    private static String pendingState;

    private OAuthState() {
    }

    public static void set(@Nullable String provider, @Nullable String state) {
        pendingProvider = provider;
        pendingState = state;
    }

    @Nullable
    public static String provider() {
        return pendingProvider;
    }

    public static boolean matches(@Nullable String candidate) {
        return pendingState != null && pendingState.equals(candidate);
    }

    public static void clear() {
        pendingProvider = null;
        pendingState = null;
    }
}
