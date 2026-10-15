package ru.dndmemory.data.remote;

import java.io.IOException;

/**
 * Обновление access-токена по refresh-токену. Реализуется
 * {@link ru.dndmemory.data.repo.AuthRepository} и вызывается из
 * {@link TokenAuthenticator} на фоновом потоке OkHttp.
 */
public interface TokenRefresher {

    /**
     * @return новый access-токен или {@code null}, если обновить не удалось
     * (тогда сессия сбрасывается и приложение возвращает пользователя на вход).
     */
    String refreshBlocking() throws IOException;
}
