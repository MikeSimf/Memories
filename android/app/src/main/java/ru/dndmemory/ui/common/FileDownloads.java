package ru.dndmemory.ui.common;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import ru.dndmemory.App;
import ru.dndmemory.R;
import ru.dndmemory.data.remote.ApiClient;

/**
 * Скачивание документов материалов и отметка «скачано».
 *
 * <p>Файл кладётся в общую папку «Загрузки» через {@link DownloadManager},
 * а признак скачивания хранится локально: на сервер он не уходит, потому что
 * это состояние устройства, а не объекта.
 */
public final class FileDownloads {

    private static final String PREFS = "dnd_downloads";
    private static final String KEY = "downloaded";

    private FileDownloads() {
    }

    public static boolean isDownloaded(@NonNull Context context, @Nullable String fileId) {
        if (fileId == null) {
            return false;
        }
        return prefs(context).getStringSet(KEY, Collections.<String>emptySet()).contains(fileId);
    }

    /** Ставит файл в очередь DownloadManager и помечает его скачанным. */
    public static void download(@NonNull Context context, @Nullable String fileId,
                                @Nullable String name) {
        if (fileId == null || fileId.isEmpty()) {
            return;
        }
        mark(context, fileId);
        String fileName = name == null || name.trim().isEmpty() ? fileId : name.trim();
        DownloadManager.Request request = new DownloadManager.Request(
                Uri.parse(ApiClient.baseUrl() + "api/files/" + fileId))
                .setTitle(fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
        // Файлы на сервере закрыты авторизацией: без токена скачивание вернёт 401.
        request.addRequestHeader("Authorization",
                "Bearer " + App.container(context).sessionStore().accessToken());
        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) {
            ViewUtils.toast(context, R.string.file_download_failed);
            return;
        }
        manager.enqueue(request);
        ViewUtils.toast(context, R.string.file_download_started);
    }

    private static void mark(@NonNull Context context, @NonNull String fileId) {
        Set<String> current = new HashSet<>(prefs(context).getStringSet(KEY, Collections.<String>emptySet()));
        current.add(fileId);
        prefs(context).edit().putStringSet(KEY, current).apply();
    }

    private static android.content.SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
