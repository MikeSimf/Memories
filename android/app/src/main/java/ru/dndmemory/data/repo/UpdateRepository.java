package ru.dndmemory.data.repo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ru.dndmemory.BuildConfig;
import ru.dndmemory.data.remote.ApiCall;
import ru.dndmemory.data.remote.ApiService;
import ru.dndmemory.data.remote.dto.AppUpdateDto;
import ru.dndmemory.util.AppExecutors;
import ru.dndmemory.util.RepoCallback;

/**
 * Проверка обновлений ({@code GET /api/app/latest}).
 *
 * <p>Сравниваем {@code versionCode}: ниже {@code minVersionCode} — работать
 * нельзя, обновление обязательное; ниже {@code versionCode} — просто есть новая
 * версия. Скачивать apk каждый раз не нужно: ссылка ведёт на наш сервер.
 */
public class UpdateRepository {

    /** Что показать пользователю. */
    public static final class UpdateInfo {
        public final boolean required;
        public final String versionName;
        public final String url;
        public final String notes;

        UpdateInfo(boolean required, @Nullable String versionName, @Nullable String url,
                   @Nullable String notes) {
            this.required = required;
            this.versionName = versionName;
            this.url = url;
            this.notes = notes;
        }
    }

    private final ApiService api;
    private final AppExecutors exec;

    public UpdateRepository(@NonNull ApiService api, @NonNull AppExecutors exec) {
        this.api = api;
        this.exec = exec;
    }

    /** @param callback получает {@code null}, если обновление не требуется. */
    public void check(@NonNull RepoCallback<UpdateInfo> callback) {
        exec.io(() -> {
            AppUpdateDto dto = ApiCall.execute(api.appLatest());
            if (dto == null) {
                return null;
            }
            int current = BuildConfig.VERSION_CODE;
            if (dto.versionCode <= current) {
                return null;
            }
            boolean required = current < dto.minVersionCode;
            return new UpdateInfo(required, dto.versionName, dto.url, dto.notes);
        }, callback);
    }
}
