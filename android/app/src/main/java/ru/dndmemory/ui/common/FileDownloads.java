package ru.dndmemory.ui.common;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import ru.dndmemory.App;
import ru.dndmemory.R;
import ru.dndmemory.data.remote.ApiClient;
import ru.dndmemory.util.AppExecutors;
import ru.dndmemory.util.RepoCallback;

/**
 * Скачивание документов в папку приложения и открытие их системным просмотрщиком.
 *
 * <p>Раньше файл уходил в общую папку «Загрузки» через DownloadManager — отсюда
 * уведомление в шторке и имя {@code document.pdf}. Теперь файл лежит внутри
 * приложения под именем, которое подготовил сервер (транслит названия материала),
 * в шторке ничего не висит, а кнопка «Открыть» открывает уже скачанное.
 */
public final class FileDownloads {

    private static final String PREFS = "dnd_downloads";
    private static final String PREFIX_PATH = "path:";
    private static final String PREFIX_TYPE = "type:";

    private static final Pattern FILENAME_STAR =
            Pattern.compile("filename\\*=UTF-8''([^;]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FILENAME_PLAIN =
            Pattern.compile("filename=\"?([^\";]+)\"?", Pattern.CASE_INSENSITIVE);

    private FileDownloads() {
    }

    public static boolean isDownloaded(@NonNull Context context, @Nullable String fileId) {
        return fileId != null && file(context, fileId) != null;
    }

    /** Имя скачанного файла — его и показываем в списке. */
    @Nullable
    public static String savedName(@NonNull Context context, @Nullable String fileId) {
        File file = file(context, fileId);
        return file == null ? null : file.getName();
    }

    /**
     * Скачивает файл в папку приложения. {@code title} — название материала:
     * сервер делает из него имя для скачивания.
     */
    public static void download(@NonNull Context context, @Nullable String fileId,
                                @Nullable String title, @Nullable String contentType,
                                @Nullable final Runnable onDone) {
        if (fileId == null || fileId.isEmpty()) {
            return;
        }
        final Context app = context.getApplicationContext();
        ViewUtils.toast(app, R.string.file_download_started);
        AppExecutors.get().io(() -> {
            File dir = new File(app.getExternalFilesDir(null), "files");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("Нет папки для файлов");
            }
            String url = ApiClient.baseUrl() + "api/files/" + fileId
                    + (title == null || title.isEmpty() ? "" : "?name=" + Uri.encode(title));
            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer "
                            + App.container(app).sessionStore().accessToken())
                    .build();
            OkHttpClient client = new OkHttpClient();
            try (Response response = client.newCall(request).execute()) {
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) {
                    throw new IllegalStateException("HTTP " + response.code());
                }
                String name = nameFrom(response.header("Content-Disposition"));
                if (name == null || name.isEmpty()) {
                    name = "file-" + fileId.substring(0, Math.min(8, fileId.length()));
                }
                File target = new File(dir, safeName(name));
                try (InputStream in = body.byteStream();
                     OutputStream out = new FileOutputStream(target)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                    }
                }
                prefs(app).edit()
                        .putString(PREFIX_PATH + fileId, target.getAbsolutePath())
                        .putString(PREFIX_TYPE + fileId,
                                contentType == null ? "application/octet-stream" : contentType)
                        .apply();
                return target;
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }, new RepoCallback<File>() {
            @Override
            public void onSuccess(@Nullable File file) {
                ViewUtils.toast(app, R.string.file_downloaded);
                if (onDone != null) {
                    onDone.run();
                }
            }

            @Override
            public void onError(@NonNull ru.dndmemory.data.remote.ApiException error) {
                ViewUtils.toast(app, R.string.file_download_failed);
            }
        });
    }

    /** Открывает скачанный файл во внешнем приложении. */
    public static void open(@NonNull Context context, @Nullable String fileId) {
        File file = file(context, fileId);
        if (file == null || fileId == null) {
            ViewUtils.toast(context, R.string.file_not_downloaded);
            return;
        }
        String type = prefs(context).getString(PREFIX_TYPE + fileId, "application/octet-stream");
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".files", file);
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, type == null ? "application/octet-stream" : type)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (RuntimeException e) {
            // Нет приложения для pdf/docx — предлагаем системный выбор.
            ViewUtils.toast(context, R.string.file_no_viewer);
        }
    }

    @Nullable
    private static File file(@NonNull Context context, @Nullable String fileId) {
        if (fileId == null) {
            return null;
        }
        String path = prefs(context).getString(PREFIX_PATH + fileId, null);
        if (path == null) {
            return null;
        }
        File file = new File(path);
        return file.exists() ? file : null;
    }

    /** Имя из заголовка ответа: сначала filename*, потом filename. */
    @Nullable
    private static String nameFrom(@Nullable String disposition) {
        if (disposition == null) {
            return null;
        }
        Matcher star = FILENAME_STAR.matcher(disposition);
        if (star.find()) {
            try {
                return Uri.decode(star.group(1).trim());
            } catch (RuntimeException ignored) {
                // Оставляем запасной вариант ниже.
            }
        }
        Matcher plain = FILENAME_PLAIN.matcher(disposition);
        return plain.find() ? plain.group(1).trim() : null;
    }

    /** Убирает то, что файловая система не примет. */
    private static String safeName(String name) {
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "-").trim();
        if (cleaned.isEmpty()) {
            return "file";
        }
        return cleaned.length() > 100 ? cleaned.substring(0, 100) : cleaned;
    }

    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
