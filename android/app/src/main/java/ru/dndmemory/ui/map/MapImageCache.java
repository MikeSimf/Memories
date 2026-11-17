package ru.dndmemory.ui.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;

/** Локальный кэш фона карты: файл называется по id картинки. */
public final class MapImageCache {

    private MapImageCache() {
    }

    @Nullable
    public static Bitmap load(@NonNull Context context, @Nullable String fileId) {
        File file = file(context, fileId);
        return file != null && file.isFile() ? BitmapFactory.decodeFile(file.getAbsolutePath()) : null;
    }

    public static void save(@NonNull Context context, @Nullable String fileId, @NonNull Bitmap bitmap) {
        File file = file(context, fileId);
        if (file == null) return;
        File dir = file.getParentFile();
        if (dir != null && !dir.exists()) dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception ignored) {
            // Кэш не критичен: при ошибке картинка просто загрузится снова.
        }
    }

    @Nullable
    private static File file(@NonNull Context context, @Nullable String fileId) {
        if (fileId == null || fileId.isEmpty()) return null;
        return new File(new File(context.getCacheDir(), "maps"), fileId + ".png");
    }
}
