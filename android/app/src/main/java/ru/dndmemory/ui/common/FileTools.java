package ru.dndmemory.ui.common;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Чтение выбранного файла без перекодирования: документы (pdf, docx) нельзя
 * прогонять через сжатие картинок — они от этого ломаются.
 */
public final class FileTools {

    /** Сервер принимает файлы до 10 МБ; проверяем заранее, чтобы не гонять зря. */
    public static final int MAX_BYTES = 10 * 1024 * 1024;

    private FileTools() {
    }

    @Nullable
    public static byte[] read(@NonNull Context context, @NonNull Uri uri, int maxBytes) throws IOException {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) > 0) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("too big");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }
}
