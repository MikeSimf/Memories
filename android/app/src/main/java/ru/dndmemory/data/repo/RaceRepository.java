package ru.dndmemory.data.repo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import ru.dndmemory.data.remote.ApiCall;
import ru.dndmemory.data.remote.ApiService;
import ru.dndmemory.data.remote.dto.RaceDto;
import ru.dndmemory.util.AppExecutors;
import ru.dndmemory.util.RepoCallback;

/**
 * Справочник рас ({@code GET /api/races}). Список меняется редко, поэтому
 * держим его в памяти на всё время работы: редактор открывается без сети.
 */
public class RaceRepository {

    private final ApiService api;
    private final AppExecutors exec;
    private volatile List<String> cached = new ArrayList<>();

    public RaceRepository(@NonNull ApiService api, @NonNull AppExecutors exec) {
        this.api = api;
        this.exec = exec;
    }

    /** Уже загруженные названия: можно показывать подсказки сразу. */
    @NonNull
    public List<String> cachedNames() {
        return cached;
    }

    /** Перечитывает справочник с сервера; ошибку вызывающий может игнорировать. */
    public void refresh(@Nullable RepoCallback<List<String>> callback) {
        exec.io(() -> {
            List<RaceDto> remote = ApiCall.execute(api.races());
            List<String> names = new ArrayList<>();
            if (remote != null) {
                for (RaceDto race : remote) {
                    if (race != null && race.name != null) {
                        names.add(race.name);
                    }
                }
            }
            cached = names;
            return names;
        }, callback);
    }
}
