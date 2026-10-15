package ru.dndmemory.ui.auth;

import androidx.annotation.Nullable;

/**
 * Одноразовое значение {@code state} для OAuth: защищает от подмены ответа.
 * Живёт в памяти процесса — если система убила процесс во время входа,
 * приложение попросит повторить попытку.
 *
 * <p>Здесь же запоминается приглашение, с которого начался вход: после возврата
 * по deep link {@code dndmemory://oauth} пользователя нужно вернуть именно на него.
 */
public final class OAuthState {

    private static String pendingProvider;
    private static String pendingState;
    @Nullable
    private static String pendingInviteToken;

    private OAuthState() {
    }

    public static void set(@Nullable String provider, @Nullable String state) {
        set(provider, state, null);
    }

    public static void set(@Nullable String provider, @Nullable String state,
                           @Nullable String inviteToken) {
        pendingProvider = provider;
        pendingState = state;
        pendingInviteToken = inviteToken;
    }

    @Nullable
    public static String provider() {
        return pendingProvider;
    }

    /** Приглашение, с которого начался вход, — если он был начат со ссылки. */
    @Nullable
    public static String inviteToken() {
        return pendingInviteToken;
    }

    public static boolean matches(@Nullable String candidate) {
        return pendingState != null && pendingState.equals(candidate);
    }

    public static void clear() {
        pendingProvider = null;
        pendingState = null;
        pendingInviteToken = null;
    }
}
