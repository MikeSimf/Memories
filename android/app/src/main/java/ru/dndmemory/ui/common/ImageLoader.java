package ru.dndmemory.ui.common;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
            return BitmapFactory.decodeStream(body.byteStream());
        } catch (IOException e) {
            return null;
        }
    }
}
