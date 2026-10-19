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
    private volatile List<String> cachedClasses = new ArrayList<>();
    private volatile List<String> cachedLocationTypes = new ArrayList<>();
    private volatile List<String> cachedItemTypes = new ArrayList<>();

    public RaceRepository(@NonNull ApiService api, @NonNull AppExecutors exec) {
        this.api = api;
        this.exec = exec;
    }

    /** Уже загруженные названия: можно показывать подсказки сразу. */
    @NonNull
    public List<String> cachedNames() {
        return cached;
    }

    /** Названия классов персонажа. */
    @NonNull
    public List<String> cachedClasses() {
        return cachedClasses;
    }

    /** Перечитывает справочник классов. */
    public void refreshClasses(@Nullable RepoCallback<List<String>> callback) {
        exec.io(() -> {
            List<RaceDto> remote = ApiCall.execute(api.classes());
            List<String> names = new ArrayList<>();
            if (remote != null) {
                for (RaceDto item : remote) {
                    if (item != null && item.name != null) {
                        names.add(item.name);
                    }
                }
            }
            cachedClasses = names;
            return names;
        }, callback);
    }

    /** Названия типов локаций. */
    @NonNull
    public List<String> cachedLocationTypes() {
        return cachedLocationTypes;
    }

    /** Названия типов предметов. */
    @NonNull
    public List<String> cachedItemTypes() {
        return cachedItemTypes;
    }

    /** Перечитывает справочник типов локаций. */
    public void refreshLocationTypes(@Nullable RepoCallback<List<String>> callback) {
        exec.io(() -> cachedLocationTypes = loadNames(ApiCall.execute(api.locationTypes())), callback);
    }

    /** Перечитывает справочник типов предметов. */
    public void refreshItemTypes(@Nullable RepoCallback<List<String>> callback) {
        exec.io(() -> cachedItemTypes = loadNames(ApiCall.execute(api.itemTypes())), callback);
    }

    @NonNull
    private static List<String> loadNames(@Nullable List<RaceDto> remote) {
        List<String> names = new ArrayList<>();
        if (remote != null) {
            for (RaceDto item : remote) {
                if (item != null && item.name != null) {
                    names.add(item.name);
                }
            }
        }
        return names;
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
