package ru.dndmemory.ui.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.data.model.EntityType;
import ru.dndmemory.data.remote.ApiException;
import ru.dndmemory.data.repo.EntityRepository;
import ru.dndmemory.sync.SyncScheduler;
import ru.dndmemory.util.AppExecutors;
import ru.dndmemory.util.RepoCallback;
import ru.dndmemory.util.Resource;

/**
 * Вкладка объектов: «Персонажи» ({@code HERO,CHARACTER}), «Локации»
 * ({@code LOCATION}) или «Предметы» ({@code ITEM}) — тот же фильтр уходит
 * и в API, и в Room.
 *
 * <p>Быстрый фильтр считает список в фоне: пока пользователь набирает, прежние
 * проходы помечаются устаревшими по номеру поколения, поэтому на списке в сотни
 * элементов интерфейс не подвисает.
 */
public class EntityListViewModel extends ViewModel {

    private final EntityRepository entities;
    private final SyncScheduler scheduler;
    private final AppExecutors exec;

    private final MediatorLiveData<List<EntityRoom>> items = new MediatorLiveData<>();
    private final MutableLiveData<Resource<Void>> refreshState = new MutableLiveData<>();
    /** Сколько объектов во вкладке до фильтрации — для правильного пустого состояния. */
    private final MutableLiveData<Integer> total = new MutableLiveData<>(0);
    private final AtomicInteger filterGeneration = new AtomicInteger();

    private String campaignId;
    private String kindsCsv = "";
    private List<String> kinds = new ArrayList<>();
    /** Полный список вкладки до фильтрации. */
    private List<EntityRoom> all = new ArrayList<>();
    private String query = "";
    private boolean initialized;

    public EntityListViewModel(@NonNull EntityRepository entities, @NonNull SyncScheduler scheduler,
                               @NonNull AppExecutors exec) {
        this.entities = entities;
        this.scheduler = scheduler;
        this.exec = exec;
    }

    /** Вызывается один раз: ViewModel переживает пересоздание View. */
    public void init(@NonNull String campaignId, @NonNull String kindsCsv) {
        if (initialized) {
            return;
        }
        initialized = true;
        this.campaignId = campaignId;
        this.kindsCsv = kindsCsv;
        this.kinds = EntityType.parseList(kindsCsv);
        items.addSource(entities.observeByKinds(campaignId, kinds), list -> {
            all = list == null ? new ArrayList<>() : list;
            total.setValue(all.size());
            applyFilter();
        });
    }

    /** Список вкладки после фильтра. */
    @NonNull
    public LiveData<List<EntityRoom>> items() {
        return items;
    }

    /** Всего объектов во вкладке (без учёта фильтра). */
    @NonNull
    public LiveData<Integer> total() {
        return total;
    }

    @NonNull
    public LiveData<Resource<Void>> refreshState() {
        return refreshState;
    }

    /** Строка фильтра вкладки: {@code HERO,CHARACTER}. */
    @NonNull
    public String kindsCsv() {
        return kindsCsv;
    }

    /** Класс, которым создаётся объект кнопкой «Добавить» на этой вкладке. */
    @NonNull
    public String createKind() {
        return EntityType.createTypeFor(kindsCsv);
    }

    /** Вкладка содержит несколько классов («Персонажи») — тогда спрашиваем класс. */
    public boolean hasSeveralKinds() {
        return kinds.size() > 1;
    }

    @NonNull
    public String campaignId() {
        return campaignId;
    }

    /** Запрос быстрого фильтра: без учёта регистра по названию и описанию. */
    public void setQuery(@Nullable String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.equals(query)) {
            return;
        }
        query = normalized;
        applyFilter();
    }

    /** Онлайн-обновление списка вкладки; фоновая синхронизация идёт своим ходом. */
    public void refresh() {
        refreshState.setValue(Resource.loading());
        entities.refresh(campaignId, kinds, new RepoCallback<List<EntityRoom>>() {
            @Override
            public void onSuccess(@Nullable List<EntityRoom> value) {
                refreshState.setValue(Resource.success(null));
            }

            @Override
            public void onError(@NonNull ApiException error) {
                refreshState.setValue(Resource.error(error));
            }
        });
    }

    /** Обновление одного объекта с сервера (действие свайпа). */
    public void refreshOne(@NonNull EntityRoom entity) {
        refreshState.setValue(Resource.loading());
        entities.refreshOne(campaignId, EntityType.fromOrCharacter(entity.kind), entity.id,
                new RepoCallback<EntityRoom>() {
                    @Override
                    public void onSuccess(@Nullable EntityRoom value) {
                        refreshState.setValue(Resource.success(null));
                    }

                    @Override
                    public void onError(@NonNull ApiException error) {
                        refreshState.setValue(Resource.error(error));
                    }
                });
    }

    /** Удаление выбранных объектов: локально сразу, отправка — синхронизацией. */
    public void deleteMany(@NonNull List<EntityRoom> selection) {
        List<String> ids = new ArrayList<>();
        for (EntityRoom room : selection) {
            if (room != null && room.id != null) {
                ids.add(room.id);
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        refreshState.setValue(Resource.loading());
        entities.deleteMany(ids, new RepoCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void value) {
                refreshState.setValue(Resource.success(null));
                scheduler.syncNow(campaignId);
            }

            @Override
            public void onError(@NonNull ApiException error) {
                refreshState.setValue(Resource.error(error));
            }
        });
    }

    public void syncNow() {
        scheduler.syncNow(campaignId);
    }

    public void clearRefreshState() {
        refreshState.setValue(null);
    }

    /** Фильтрация в фоне: устаревшие проходы отбрасываются по номеру поколения. */
    private void applyFilter() {
        final int generation = filterGeneration.incrementAndGet();
        final List<EntityRoom> snapshot = new ArrayList<>(all);
        final String needle = query == null ? "" : query;
        exec.io(() -> {
            List<EntityRoom> filtered = filter(snapshot, needle);
            if (generation == filterGeneration.get()) {
                items.postValue(filtered);
            }
        });
    }

    /**
     * Совпадение без учёта регистра: название и описание, а у персонажа ещё
     * и раса с кратким описанием (docs/api.md, раздел 8).
     */
    @NonNull
    static List<EntityRoom> filter(@NonNull List<EntityRoom> source, @NonNull String query) {
        if (query.isEmpty()) {
            return source;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        List<EntityRoom> out = new ArrayList<>();
        for (EntityRoom room : source) {
            if (matches(room, needle)) {
                out.add(room);
            }
        }
        return out;
    }

    private static boolean matches(@NonNull EntityRoom room, @NonNull String needle) {
        if (room.name != null && room.name.toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        if (room.description != null
                && room.description.toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        if (EntityType.CHARACTER.name().equalsIgnoreCase(room.kind)) {
            if (room.race != null && room.race.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
            return room.shortDescription != null
                    && room.shortDescription.toLowerCase(Locale.ROOT).contains(needle);
        }
        return false;
    }
}
