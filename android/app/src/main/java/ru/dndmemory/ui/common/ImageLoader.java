package ru.dndmemory.ui.common;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import ru.dndmemory.data.remote.ApiClient;

/**
 * Маленький загрузчик картинок объектов: GET {@code /api/files/{id}} с токеном.
 *
 * <p>Внешнюю библиотеку не тащим: нужен один экран и один тип запроса.
 * Кэш в памяти по id файла, поэтому повторное открытие карточки не ходит в сеть.
 */
public final class ImageLoader {

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>(8 * 1024 * 1024) {
                @Override
                protected int sizeOf(@NonNull String key, @NonNull Bitmap value) {
                    return value.getByteCount();
                }
            };

    private ImageLoader() {
    }

    /**
     * Показывает картинку файла в {@code target}. Токен уже добавлен
     * авторизованным клиентом OkHttp.
     */
    public static void load(@NonNull final OkHttpClient client, @Nullable final String fileId,
                            @NonNull final ImageView target) {
        if (fileId == null || fileId.isEmpty()) {
            target.setImageDrawable(null);
            ViewUtils.show(target, false);
            return;
        }
        target.setTag(fileId);
        Bitmap cached = CACHE.get(fileId);
        if (cached != null) {
            target.setImageBitmap(cached);
            ViewUtils.show(target, true);
            return;
        }
        ViewUtils.show(target, true);
        POOL.execute(() -> {
            Bitmap bitmap = fetch(client, fileId);
            if (bitmap == null) {
                return;
            }
            CACHE.put(fileId, bitmap);
            MAIN.post(() -> {
                // Пока грузили, карточка могла открыть другой объект.
                if (fileId.equals(target.getTag())) {
                    target.setImageBitmap(bitmap);
                }
            });
        });
    }

    /** Картинка целиком — для полноэкранного просмотра. */
    public static void loadFull(@NonNull final OkHttpClient client, @NonNull final String fileId,
                                @NonNull final java.util.function.Consumer<Bitmap> onLoaded) {
        Bitmap cached = CACHE.get(fileId);
        if (cached != null) {
            onLoaded.accept(cached);
            return;
        }
        POOL.execute(() -> {
            Bitmap bitmap = fetch(client, fileId);
            if (bitmap == null) {
                return;
            }
            CACHE.put(fileId, bitmap);
            MAIN.post(() -> onLoaded.accept(bitmap));
        });
    }


    /** Учитывает EXIF-поворот JPEG: камера/телефон часто пишет его вместо поворота пикселей. */
    @NonNull
    private static Bitmap applyOrientation(@NonNull Bitmap bitmap, int orientation) {
        if (orientation == 1) return bitmap;
        Matrix matrix = new Matrix();
        switch (orientation) {
            case 2: matrix.setScale(-1, 1); break;
            case 3: matrix.setRotate(180); break;
            case 4: matrix.setScale(1, -1); break;
            case 5: matrix.setRotate(90); matrix.postScale(-1, 1); break;
            case 6: matrix.setRotate(90); break;
            case 7: matrix.setRotate(270); matrix.postScale(-1, 1); break;
            case 8: matrix.setRotate(270); break;
            default: return bitmap;
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    /** Минимальный разбор APP1/EXIF: нужен только тег Orientation (0x0112). */
    private static int jpegOrientation(@NonNull byte[] data) {
        if (data.length < 4 || (data[0] & 0xFF) != 0xFF || (data[1] & 0xFF) != 0xD8) return 1;
        int pos = 2;
        while (pos + 4 < data.length) {
            if ((data[pos] & 0xFF) != 0xFF) { pos++; continue; }
            int marker = data[pos + 1] & 0xFF;
            pos += 2;
            if (marker == 0xDA || marker == 0xD9) break;
            if (marker == 0x00 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD8)) continue;
            if (pos + 2 > data.length) break;
            int length = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
            if (length < 2 || pos + length > data.length) break;
            if (marker == 0xE1 && length >= 10 && data[pos + 2] == 'E' && data[pos + 3] == 'x'
                    && data[pos + 4] == 'i' && data[pos + 5] == 'f') {
                int tiff = pos + 8;
                if (tiff + 8 > data.length) return 1;
                boolean little = data[tiff] == 'I' && data[tiff + 1] == 'I';
                int ifd = tiff + readInt(data, tiff + 4, little);
                if (ifd + 2 > data.length) return 1;
                int count = readShort(data, ifd, little);
                for (int i = 0; i < count; i++) {
                    int entry = ifd + 2 + i * 12;
                    if (entry + 12 > data.length) break;
                    if (readShort(data, entry, little) == 0x0112) {
                        return readShort(data, entry + 8, little);
                    }
                }
            }
            pos += length;
        }
        return 1;
    }

    private static int readShort(byte[] data, int pos, boolean little) {
        int a = data[pos] & 0xFF, b = data[pos + 1] & 0xFF;
        return little ? (a | (b << 8)) : ((a << 8) | b);
    }

    private static int readInt(byte[] data, int pos, boolean little) {
        int a = data[pos] & 0xFF, b = data[pos + 1] & 0xFF;
        int c = data[pos + 2] & 0xFF, d = data[pos + 3] & 0xFF;
        return little ? (a | (b << 8) | (c << 16) | (d << 24))
                : ((a << 24) | (b << 16) | (c << 8) | d);
    }

    @Nullable
    private static Bitmap fetch(@NonNull OkHttpClient client, @NonNull String fileId) {
        Request request = new Request.Builder()
                .url(ApiClient.baseUrl() + "api/files/" + fileId)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return null;
            }
            ResponseBody body = response.body();
            if (body == null) {
                return null;
            }
            byte[] bytes = body.bytes();
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            return applyOrientation(bitmap, jpegOrientation(bytes));
        } catch (IOException e) {
            return null;
        }
    }
}
