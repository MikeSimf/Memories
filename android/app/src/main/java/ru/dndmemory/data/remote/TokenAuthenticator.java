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
 * Реакция на 401: обновляет access-токен (refresh-токен ротируется сервером)
 * и повторяет запрос ровно один раз.
 *
 * <p><b>Без зацикливания.</b> Повторный 401 означает, что сессия недействительна:
 * второй раз refresh не вызываем. Признак отзыва передаёт
 * {@link ru.dndmemory.data.repo.AuthRepository#refreshBlocking()} — он чистит
 * токены и поднимает событие для экрана QR, а локальные данные в Room остаются.
 */
public class TokenAuthenticator implements Authenticator {

    /** Метка повтора: по ней второй 401 отличается от первого. */
    private static final String HEADER_RETRIED = "X-Dnd-Retried";

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
        if (response.request().header(HEADER_RETRIED) != null || countRetries(response) >= 2) {
            // Токен уже обновляли, сервер снова ответил 401 — сессия недействительна.
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
            // Токены уже очищены в refreshBlocking — повтор запроса не поможет.
            return null;
        }
        return withToken(response.request(), fresh);
    }

    private static Request withToken(Request request, String token) {
        return request.newBuilder()
                .header("Authorization", "Bearer " + token)
                .header(HEADER_RETRIED, "1")
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
