package ru.dndmemory.ui.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;
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
 * Вкладка объектов: «Персонажи» ({@code HERO,NPC}), «Локации» ({@code LOCATION})
 * или «Предметы» ({@code ITEM}) — тот же фильтр уходит и в API, и в Room.
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
    private String typesCsv = "";
    private List<String> types = new ArrayList<>();
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
    public void init(@NonNull String campaignId, @NonNull String typesCsv) {
        if (initialized) {
            return;
        }
        initialized = true;
        this.campaignId = campaignId;
        this.typesCsv = typesCsv;
        this.types = EntityType.parseList(typesCsv);
        items.addSource(entities.observeByTypes(campaignId, types), list -> {
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

    /** Строка фильтра вкладки: {@code HERO,NPC}. */
    @NonNull
    public String typesCsv() {
        return typesCsv;
    }

    /** Тип, которым создаётся объект кнопкой «Добавить» на этой вкладке. */
    @NonNull
    public String createType() {
        return EntityType.createTypeFor(typesCsv);
    }

    @NonNull
    public String campaignId() {
        return campaignId;
    }

    /** Запрос быстрого фильтра: без учёта регистра по названию, описанию и меткам. */
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
        entities.refresh(campaignId, types, new RepoCallback<List<EntityRoom>>() {
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

    /** Совпадение без учёта регистра: название, краткое описание, описание и метки. */
    @NonNull
    static List<EntityRoom> filter(@NonNull List<EntityRoom> source, @NonNull String query) {
        if (query.isEmpty()) {
            return source;
        }
        String needle = query.toLowerCase(java.util.Locale.ROOT);
        List<EntityRoom> out = new ArrayList<>();
        for (EntityRoom room : source) {
            if (matches(room, needle)) {
                out.add(room);
            }
        }
        return out;
    }

    private static boolean matches(@NonNull EntityRoom room, @NonNull String needle) {
        if (room.name != null && room.name.toLowerCase(java.util.Locale.ROOT).contains(needle)) {
            return true;
        }
        if (room.summary != null && room.summary.toLowerCase(java.util.Locale.ROOT).contains(needle)) {
            return true;
        }
        if (room.description != null
                && room.description.toLowerCase(java.util.Locale.ROOT).contains(needle)) {
            return true;
        }
        if (room.tags != null) {
            for (String tag : room.tags) {
                if (tag != null && tag.toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                    return true;
                }
            }
        }
        return false;
    }
}
