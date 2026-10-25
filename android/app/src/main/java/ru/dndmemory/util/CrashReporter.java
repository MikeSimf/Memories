package ru.dndmemory.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import ru.dndmemory.BuildConfig;

/** Отправляет стектрейс падения на сервер и повторяет отправку при следующем запуске. */
public final class CrashReporter {

    private static final String TAG = "CrashReporter";
    private static final String PREFS = "crash_reporter";
    private static final String KEY_PENDING = "pending";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private CrashReporter() {
    }

    public static void install(@NonNull Context context) {
        Context app = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                String stack = stack(error);
                save(app, stack);
                upload(app, stack);
                Thread.sleep(700);
            } catch (Throwable ignored) {
                // падение при отправке не должно мешать закрытию приложения
            }
            if (previous != null) {
                previous.uncaughtException(thread, error);
            } else {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        });
        // если прошлый раз не успели отправить — пробуем сейчас
        String pending = prefs(app).getString(KEY_PENDING, null);
        if (pending != null && !pending.isEmpty()) {
            upload(app, pending);
        }
    }

    private static void save(Context context, String stack) {
        prefs(context).edit().putString(KEY_PENDING, stack).apply();
    }

    private static void upload(Context context, String stack) {
        AppExecutors.get().io(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .writeTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .build();
                org.json.JSONObject body = new org.json.JSONObject();
                body.put("stacktrace", stack);
                body.put("appVersion", BuildConfig.VERSION_NAME);
                body.put("device", Build.MANUFACTURER + " " + Build.MODEL + ", Android " + Build.VERSION.RELEASE);
                String base = BuildConfig.DND_SERVER_URL;
                if (!base.endsWith("/")) base += "/";
                Request request = new Request.Builder()
                        .url(base + "api/crashes")
                        .post(RequestBody.create(body.toString(), JSON))
                        .build();
                try (okhttp3.Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        prefs(context).edit().remove(KEY_PENDING).apply();
                    }
                }
            } catch (Throwable error) {
                Log.w(TAG, "Не удалось отправить отчёт о падении", error);
            }
            return null;
        }, new RepoCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void value) {
            }

            @Override
            public void onError(@NonNull ru.dndmemory.data.remote.ApiException error) {
            }
        });
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String stack(Throwable error) {
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
