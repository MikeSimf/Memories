package ru.dndmemory.ui.common;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Подготовка картинки к отправке: уменьшаем до разумной стороны и сжимаем
 * в JPEG, чтобы фото с телефона не уезжало на сервер на 8 МБ.
 */
public final class ImageTools {

    /** Длинная сторона после сжатия: хватает для карточки на любом экране. */
    private static final int MAX_SIDE = 1600;
    /** Предел отправляемого файла; сервер принимает до 10 МБ. */
    private static final int MAX_BYTES = 2 * 1024 * 1024;

    private ImageTools() {
    }

    @Nullable
    public static byte[] compress(@NonNull Context context, @NonNull Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                return null;
            }
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
        Bitmap bitmap;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                return null;
            }
            bitmap = BitmapFactory.decodeStream(in, null, options);
        }
        if (bitmap == null) {
            return null;
        }

        Bitmap scaled = scaleDown(bitmap, MAX_SIDE);
        try {
            int quality = 85;
            byte[] data = toJpeg(scaled, quality);
            // Если получилось тяжело — поджимаем качество, но не ниже 55.
            while (data != null && data.length > MAX_BYTES && quality > 55) {
                quality -= 10;
                data = toJpeg(scaled, quality);
            }
            return data;
        } finally {
            if (scaled != bitmap) {
                scaled.recycle();
            }
            bitmap.recycle();
        }
    }

    private static int sampleSize(int width, int height) {
        int sample = 1;
        int longest = Math.max(width, height);
        while (longest / sample > MAX_SIDE * 2) {
            sample *= 2;
        }
        return sample;
    }

    @NonNull
    private static Bitmap scaleDown(@NonNull Bitmap source, int maxSide) {
        int longest = Math.max(source.getWidth(), source.getHeight());
        if (longest <= maxSide) {
            return source;
        }
        float ratio = (float) maxSide / longest;
        int width = Math.max(1, Math.round(source.getWidth() * ratio));
        int height = Math.max(1, Math.round(source.getHeight() * ratio));
        return Bitmap.createScaledBitmap(source, width, height, true);
    }

    @Nullable
    private static byte[] toJpeg(@NonNull Bitmap bitmap, int quality) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
            return null;
        }
        return out.toByteArray();
    }
}
