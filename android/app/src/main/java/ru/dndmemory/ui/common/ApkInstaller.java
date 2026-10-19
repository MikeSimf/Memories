package ru.dndmemory.ui.common;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import ru.dndmemory.App;
import ru.dndmemory.R;
import ru.dndmemory.util.AppExecutors;
import ru.dndmemory.util.RepoCallback;

/**
 * Скачивание и установка обновления: apk кладётся во внешнюю папку приложения
 * и отдаётся установщику через FileProvider.
 *
 * <p>Раньше «Скачать» просто открывало ссылку в браузере — приходилось искать
 * файл и ставить вручную. Теперь загрузка идёт в приложении, а после неё сразу
 * открывается системный установщик.
 */
public final class ApkInstaller {

    private ApkInstaller() {
    }

    public static void downloadAndInstall(@NonNull final Context context, @Nullable final String url) {
        if (url == null || url.isEmpty()) {
            ViewUtils.toast(context, R.string.update_no_link);
            return;
        }
        final Context app = context.getApplicationContext();
        ViewUtils.toast(app, R.string.update_downloading);
        AppExecutors.get().io(() -> {
            File dir = new File(app.getExternalFilesDir(null), "updates");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("Нет папки для загрузки");
            }
            File target = new File(dir, "dnd-memory.apk");
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(url).build();
            try (Response response = client.newCall(request).execute()) {
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) {
                    throw new IllegalStateException("HTTP " + response.code());
                }
                try (InputStream in = body.byteStream();
                     OutputStream out = new FileOutputStream(target)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                    }
                }
            }
            return target;
        }, new RepoCallback<File>() {
            @Override
            public void onSuccess(@Nullable File file) {
                if (file != null) {
                    install(app, file);
                }
            }

            @Override
            public void onError(@NonNull ru.dndmemory.data.remote.ApiException error) {
                ViewUtils.toast(app, R.string.update_failed);
            }
        });
    }

    /** Открывает системный установщик; при необходимости ведёт в настройки разрешений. */
    private static void install(@NonNull Context context, @NonNull File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !context.getPackageManager().canRequestPackageInstalls()) {
            ViewUtils.toast(context, R.string.update_allow_install);
            try {
                Intent settings = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + context.getPackageName()));
                settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(settings);
            } catch (RuntimeException ignored) {
                // Прошивка без этого экрана — останется установка вручную.
            }
            return;
        }
        Uri uri = FileProvider.getUriForFile(context,
                context.getPackageName() + ".files", file);
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (RuntimeException e) {
            ViewUtils.toast(context, R.string.update_failed);
        }
    }
}
