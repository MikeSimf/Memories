package ru.dndmemory.ui.search;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.data.local.entity.RetellingRoom;
import ru.dndmemory.data.model.EntityType;
import ru.dndmemory.data.remote.ApiException;
import ru.dndmemory.data.remote.dto.SearchItemDto;
import ru.dndmemory.data.repo.EntityRepository;
import ru.dndmemory.data.repo.RetellingRepository;
import ru.dndmemory.util.RepoCallback;
import ru.dndmemory.util.WikiMarkdown;

/**
 * Поиск. Основу даёт локальная база: та же логика, что у сервера — по {@code name}
 * и {@code description}, а у персонажа ещё по расе и краткому описанию, поэтому
 * поиск работает и без сети. Когда сеть есть, результаты дополняются ответом
 * {@code GET /api/campaigns/{id}/search?q=&kinds=} (docs/api.md, раздел 8).
 */
public class SearchViewModel extends ViewModel {

    private static final int SNIPPET_LENGTH = 160;

    private final EntityRepository entities;
    private final RetellingRepository retellings;

    private final MutableLiveData<String> query = new MutableLiveData<>("");
    private final MutableLiveData<String> kindFilter = new MutableLiveData<>();
    private final MutableLiveData<List<SearchItemDto>> remoteItems = new MutableLiveData<>();
    private final MediatorLiveData<List<SearchAdapter.Item>> results = new MediatorLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final AtomicInteger remoteGeneration = new AtomicInteger();

    private String campaignId = "";
    private LiveData<List<EntityRoom>> entitySource;
    private LiveData<List<RetellingRoom>> retellingSource;
    private List<EntityRoom> lastEntities = new ArrayList<>();
    private List<RetellingRoom> lastRetellings = new ArrayList<>();
    private boolean initialized;

    public SearchViewModel(@NonNull EntityRepository entities,
                           @NonNull RetellingRepository retellings) {
        this.entities = entities;
        this.retellings = retellings;
    }

    public void init(@NonNull String campaignId, @Nullable String initialFilter) {
        if (initialized) {
            return;
        }
        initialized = true;
        kindFilter.setValue(initialFilter);
        this.campaignId = campaignId;
        results.addSource(remoteItems, list -> combine());
        resubscribe();
    }

    /** Пересобирает подписки на локальную базу под текущий запрос и фильтр. */
    private void resubscribe() {
        List<String> kinds = kindsOf(kindFilter.getValue());
        if (entitySource != null) {
            results.removeSource(entitySource);
        }
        entitySource = entities.searchByKinds(campaignId, kinds, query.getValue());
        results.addSource(entitySource, list -> {
            lastEntities = list == null ? Collections.emptyList() : list;
            combine();
        });

        if (retellingSource != null) {
            results.removeSource(retellingSource);
        }
        retellingSource = retellings.search(campaignId, query.getValue());
        results.addSource(retellingSource, list -> {
            lastRetellings = list == null ? Collections.emptyList() : list;
            combine();
        });
    }

    @NonNull
    public LiveData<List<SearchAdapter.Item>> results() {
        return results;
    }

    @NonNull
    public LiveData<Boolean> loading() {
        return loading;
    }

    @NonNull
    public MutableLiveData<String> kindFilter() {
        return kindFilter;
    }

    public void setQuery(@Nullable String value) {
        String normalized = value == null ? "" : value;
        if (normalized.equals(query.getValue())) {
            return;
        }
        query.setValue(normalized);
        resubscribe();
        searchRemote(normalized);
    }

    /** Смена фильтра перезапускает запрос к локальной базе и к серверу. */
    public void setKindFilter(@Nullable String filter) {
        kindFilter.setValue(filter);
        resubscribe();
        searchRemote(query.getValue());
    }

    /** Серверный поиск: дополняет локальные результаты, когда есть сеть. */
    private void searchRemote(@Nullable String text) {
        if (text == null || text.trim().isEmpty()) {
            remoteItems.setValue(Collections.emptyList());
            return;
        }
        final int generation = remoteGeneration.incrementAndGet();
        final String needle = text.trim();
        List<String> kinds = kindsOf(kindFilter.getValue());
        if (isRetellingOnly(kindFilter.getValue())) {
            kinds = new ArrayList<>();
            kinds.add(EntityType.RETELLING);
        }
        loading.setValue(true);
        entities.searchRemote(campaignId, needle, kinds, new RepoCallback<ru.dndmemory.data.remote.dto.SearchResponse>() {
            @Override
            public void onSuccess(@Nullable ru.dndmemory.data.remote.dto.SearchResponse value) {
                if (generation != remoteGeneration.get()) {
                    return;
                }
                // Сеть есть — серверные результаты дополняют локальные.
                List<SearchItemDto> items = value == null || value.items == null
                        ? Collections.<SearchItemDto>emptyList() : value.items;
                remoteItems.setValue(items);
            }

            @Override
            public void onError(@NonNull ApiException error) {
                if (generation != remoteGeneration.get()) {
                    return;
                }
                // Офлайн: остаются локальные результаты.
                remoteItems.setValue(Collections.<SearchItemDto>emptyList());
            }
        });
    }

    private void combine() {
        String filter = kindFilter.getValue();
        String text = query.getValue();
        List<String> kinds = kindsOf(filter);
        boolean wantEntities = !isRetellingOnly(filter);
        boolean wantRetellings = TextUtils.isEmpty(filter) || isRetellingOnly(filter);

        List<SearchAdapter.Item> out = new ArrayList<>();
        if (text != null && !text.trim().isEmpty()) {
            if (wantEntities) {
                for (EntityRoom entity : lastEntities) {
                    out.add(new SearchAdapter.Item(SearchAdapter.KIND_ENTITY, entity.id, entity.kind,
                            TextUtils.isEmpty(entity.name) ? entity.kind : entity.name,
                            snippet(entity.description, entity.race, entity.shortDescription)));
                }
            }
            if (wantRetellings) {
                for (RetellingRoom retelling : lastRetellings) {
                    out.add(new SearchAdapter.Item(SearchAdapter.KIND_RETELLING, retelling.id, null,
                            retelling.title, snippet(retelling.body)));
                }
            }
            // Ответ сервера: добавляем только то, чего нет в локальной выдаче.
            List<SearchItemDto> remote = remoteItems.getValue();
            if (remote != null) {
                for (SearchItemDto item : remote) {
                    if (item == null || item.id == null || item.kind == null) {
                        continue;
                    }
                    boolean retelling = EntityType.RETELLING.equalsIgnoreCase(item.kind);
                    if (retelling ? !wantRetellings : !wantEntities) {
                        continue;
                    }
                    if (!retelling && !kinds.isEmpty()
                            && !kinds.contains(item.kind.toUpperCase(java.util.Locale.ROOT))) {
                        continue;
                    }
                    if (containsId(out, item.id)) {
                        continue;
                    }
                    out.add(new SearchAdapter.Item(
                            retelling ? SearchAdapter.KIND_RETELLING : SearchAdapter.KIND_ENTITY,
                            item.id, retelling ? null : item.kind,
                            item.title, stripTags(item.snippet)));
                }
            }
        }
        results.setValue(out);
        loading.setValue(false);
    }

    private static boolean containsId(@NonNull List<SearchAdapter.Item> items, @NonNull String id) {
        for (SearchAdapter.Item item : items) {
            if (id.equals(item.id)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRetellingOnly(@Nullable String filter) {
        return EntityType.RETELLING.equalsIgnoreCase(filter == null ? "" : filter.trim());
    }

    /** Пустой список — «все классы»; пересказы живут отдельной таблицей. */
    @NonNull
    private static List<String> kindsOf(@Nullable String filter) {
        if (filter == null || filter.trim().isEmpty() || isRetellingOnly(filter)) {
            return Collections.emptyList();
        }
        return EntityType.parseList(filter);
    }

    /** Сервер подсвечивает совпадения тегами {@code <b>} — в списке они лишние. */
    @NonNull
    private static CharSequence stripTags(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replaceAll("<[^>]+>", "");
    }

    @NonNull
    private static CharSequence snippet(@Nullable String... sources) {
        StringBuilder joined = new StringBuilder();
        for (String source : sources) {
            if (source == null || source.isEmpty()) {
                continue;
            }
            if (joined.length() > 0) {
                joined.append(' ');
            }
            joined.append(WikiMarkdown.plainText(source));
        }
        String value = joined.toString().trim();
        if (value.length() <= SNIPPET_LENGTH) {
            return value;
        }
        return value.substring(0, SNIPPET_LENGTH) + "…";
    }
}
