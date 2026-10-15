package ru.dndmemory.data.remote;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import ru.dndmemory.data.local.SessionStore;

/**
 * Реакция на 401: один раз обновляет access-токен (refresh-токен ротируется
 * сервером) и повторяет запрос. Если обновление не удалось — сессия сбрасывается.
 */
public class TokenAuthenticator implements Authenticator {

    private static final Object LOCK = new Object();

    private final SessionStore sessionStore;
    private final TokenRefresher refresher;

    public TokenAuthenticator(SessionStore sessionStore, TokenRefresher refresher) {
        this.sessionStore = sessionStore;
        this.refresher = refresher;
    }

    @Nullable
    @Override
    public Request authenticate(@Nullable Route route, @NonNull Response response) throws IOException {
        String failedHeader = response.request().header("Authorization");
        if (failedHeader == null) {
            // 401 без нашего токена: это вход/регистрация, повторять нечем.
            return null;
        }
        if (countRetries(response) >= 2) {
            return null;
        }
        String path = response.request().url().encodedPath();
        if (path.contains("/api/auth/refresh") || path.contains("/api/auth/login")
                || path.contains("/api/auth/register")) {
            return null;
        }

        String fresh;
        synchronized (LOCK) {
            String current = sessionStore.accessToken();
            if (current != null && !current.equals(bearer(failedHeader))) {
                // Другой поток уже обновил токен — просто повторяем с ним.
                return withToken(response.request(), current);
            }
            fresh = refresher.refreshBlocking();
        }
        if (fresh == null || fresh.isEmpty()) {
            sessionStore.clear();
            return null;
        }
        return withToken(response.request(), fresh);
    }

    private static Request withToken(Request request, String token) {
        return request.newBuilder()
                .header("Authorization", "Bearer " + token)
                .build();
    }

    private static String bearer(String header) {
        return header.startsWith("Bearer ") ? header.substring(7) : header;
    }

    private static int countRetries(Response response) {
        int count = 0;
        Response prior = response.priorResponse();
        while (prior != null) {
            count++;
            prior = prior.priorResponse();
        }
        return count;
    }
}
