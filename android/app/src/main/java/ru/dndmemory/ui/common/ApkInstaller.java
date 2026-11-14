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
import ru.dndmemory.R;
import ru.dndmemory.util.AppExecutors;
import ru.dndmemory.util.RepoCallback;

/**
 * Скачивание и установка обновления.
 *
 * <p>Файл версии хранится в папке приложения. Если apk уже скачан, но установщик
 * был закрыт, повторно качать не нужно — сразу открываем установку. Во время
 * загрузки показываем процент.
 */
public final class ApkInstaller {

    private ApkInstaller() {
    }

    /** @deprecated используйте версию с номером сборки */
    @Deprecated
    public static void downloadAndInstall(@NonNull Context context, @Nullable String url) {
        downloadAndInstall(context, url, 0);
    }

    public static void downloadAndInstall(@NonNull final Context context, @Nullable final String url,
                                          final int versionCode) {
        if (url == null || url.isEmpty()) {
            ViewUtils.toast(context, R.string.update_no_link);
            return;
        }
        final Context app = context.getApplicationContext();
        File cached = file(app, versionCode);
        if (cached != null) {
            install(context, cached);
            return;
        }
        final DownloadProgressDialog progress = DownloadProgressDialog.show(context,
                context.getString(R.string.download_progress));
        AppExecutors.get().io(() -> {
            File dir = new File(app.getExternalFilesDir(null), "updates");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("Нет папки для загрузки");
            }
            File target = new File(dir, "dnd-memory-" + versionCode + ".apk");
            File part = new File(dir, target.getName() + ".part");
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
            Request request = new Request.Builder().url(url).build();
            try (Response response = client.newCall(request).execute()) {
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) {
                    throw new IllegalStateException("HTTP " + response.code());
                }
                long total = body.contentLength();
                long done = 0;
                try (InputStream in = body.byteStream();
                     OutputStream out = new FileOutputStream(part)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                        done += read;
                        if (progress != null && total > 0) {
                            progress.progress((int) (done * 100 / total));
                        }
                    }
                }
                if (!part.renameTo(target)) {
                    throw new IllegalStateException("Не удалось сохранить apk");
                }
            } catch (Exception e) {
                //noinspection ResultOfMethodCallIgnored
                part.delete();
                throw new IllegalStateException(e);
            }
            return target;
        }, new RepoCallback<File>() {
            @Override
            public void onSuccess(@Nullable File file) {
                if (progress != null) progress.dismiss();
                if (file != null) {
                    install(context, file);
                }
            }

            @Override
            public void onError(@NonNull ru.dndmemory.data.remote.ApiException error) {
                if (progress != null) progress.dismiss();
                ViewUtils.toast(app, R.string.update_failed);
            }
        });
    }

    public static boolean hasDownloaded(@NonNull Context context, int versionCode) {
        return file(context.getApplicationContext(), versionCode) != null;
    }

    @Nullable
    private static File file(@NonNull Context context, int versionCode) {
        File dir = new File(context.getExternalFilesDir(null), "updates");
        File file = new File(dir, "dnd-memory-" + versionCode + ".apk");
        return file.isFile() && file.length() > 0 ? file : null;
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
