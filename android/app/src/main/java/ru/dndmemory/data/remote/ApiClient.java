package ru.dndmemory.data.remote;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import ru.dndmemory.BuildConfig;

/** Сборка OkHttp/Retrofit. Базовый адрес берётся из BuildConfig.DND_SERVER_URL. */
public final class ApiClient {

    private ApiClient() {
    }

    /**
     * {@code serializeNulls()} нужен для PATCH: явный {@code null} очищает поле
     * ({@code ownerHeroId}, {@code imageFileId}).
     */
    public static Gson gson() {
        return new GsonBuilder().serializeNulls().create();
    }

    public static String baseUrl() {
        return normalizeBaseUrl(BuildConfig.DND_SERVER_URL);
    }

    public static String normalizeBaseUrl(String raw) {
        String url = (raw == null || raw.trim().isEmpty())
                ? "http://10.0.2.2:8080/" : raw.trim();
        if (!url.endsWith("/")) {
            url = url + "/";
        }
        return url;
    }

    /** Клиент без авторизации: используется только для refresh и служебных запросов. */
    public static OkHttpClient plainHttpClient() {
        return baseBuilder().build();
    }

    /** Клиент с подстановкой токена и автообновлением по 401. */
    public static OkHttpClient authorizedHttpClient(OkHttpClient plain, TokenAuthenticator authenticator,
                                                   AuthInterceptor interceptor) {
        return plain.newBuilder()
                .addInterceptor(interceptor)
                .authenticator(authenticator)
                .build();
    }

    private static OkHttpClient.Builder baseBuilder() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(BuildConfig.DEBUG
                ? HttpLoggingInterceptor.Level.BASIC
                : HttpLoggingInterceptor.Level.NONE);
        return new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .addInterceptor(logging);
    }

    public static Retrofit retrofit(OkHttpClient client) {
        return retrofit(client, baseUrl());
    }

    public static Retrofit retrofit(OkHttpClient client, String baseUrl) {
        return new Retrofit.Builder()
                .baseUrl(normalizeBaseUrl(baseUrl))
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson()))
                .build();
    }

    public static ApiService create(OkHttpClient client) {
        return retrofit(client).create(ApiService.class);
    }
}
