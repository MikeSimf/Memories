package ru.dndmemory.data.remote;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.UUID;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import ru.dndmemory.data.local.SessionStore;

/**
 * Подставляет {@code Authorization: Bearer <accessToken>} и {@code X-Request-Id}
 * в каждый запрос (docs/api.md, разделы 1 и 12).
 */
public class AuthInterceptor implements Interceptor {

    private final SessionStore sessionStore;

    public AuthInterceptor(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        Request.Builder builder = original.newBuilder();

        if (original.header("Authorization") == null) {
            String token = sessionStore.accessToken();
            if (token != null && !token.isEmpty()) {
                builder.header("Authorization", "Bearer " + token);
            }
        }
        if (original.header("X-Request-Id") == null) {
            builder.header("X-Request-Id", UUID.randomUUID().toString());
        }
        if (original.header("Accept") == null) {
            builder.header("Accept", "application/json");
        }
        return chain.proceed(builder.build());
    }
}
