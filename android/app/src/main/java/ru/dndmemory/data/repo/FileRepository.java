package ru.dndmemory.data.repo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import ru.dndmemory.data.remote.ApiCall;
import ru.dndmemory.data.remote.ApiService;
import ru.dndmemory.data.remote.dto.FileDto;
import ru.dndmemory.util.AppExecutors;
import ru.dndmemory.util.RepoCallback;

/**
 * Загрузка картинок (docs/api.md, раздел 10): {@code POST /api/files}, поле
 * формы называется {@code file}. В ответ приходит {@code id}, который потом
 * сохраняется в объекте как {@code imageFileId}.
 */
public class FileRepository {

    private final ApiService api;
    private final AppExecutors exec;

    public FileRepository(@NonNull ApiService api, @NonNull AppExecutors exec) {
        this.api = api;
        this.exec = exec;
    }

    /** Отправляет уже сжатые байты картинки. */
    public void upload(@NonNull final byte[] data, @NonNull final String contentType,
                       @NonNull final String fileName, @NonNull RepoCallback<FileDto> callback) {
        exec.io(() -> {
            RequestBody body = RequestBody.create(data, MediaType.parse(contentType));
            MultipartBody.Part part = MultipartBody.Part.createFormData("file", fileName, body);
            return ApiCall.execute(api.uploadFile(part));
        }, callback);
    }
}
