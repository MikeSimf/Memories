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

import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.data.local.entity.RetellingRoom;
import ru.dndmemory.data.model.EntityType;
import ru.dndmemory.data.repo.EntityRepository;
import ru.dndmemory.data.repo.RetellingRepository;
import ru.dndmemory.util.WikiMarkdown;

/**
 * Поиск идёт по локальной базе той же логикой, что у сервера (name, summary,
 * description, tags и body пересказов), поэтому работает без сети
 * (docs/api.md, раздел 8).
 */
public class SearchViewModel extends ViewModel {

    private static final int SNIPPET_LENGTH = 160;

    private final EntityRepository entities;
    private final RetellingRepository retellings;

    private final MutableLiveData<String> query = new MutableLiveData<>("");
    private final MutableLiveData<String> typeFilter = new MutableLiveData<>();
    private final MediatorLiveData<List<SearchAdapter.Item>> results = new MediatorLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);

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
        typeFilter.setValue(initialFilter);
        this.campaignId = campaignId;
        resubscribe();
    }

    /** Пересобирает подписки на локальную базу под текущий запрос и фильтр. */
    private void resubscribe() {
        List<String> types = typesOf(typeFilter.getValue());
        if (entitySource != null) {
            results.removeSource(entitySource);
        }
        entitySource = entities.searchByTypes(campaignId, types, query.getValue());
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
    public MutableLiveData<String> typeFilter() {
        return typeFilter;
    }

    public void setQuery(@Nullable String value) {
        String normalized = value == null ? "" : value;
        if (normalized.equals(query.getValue())) {
            return;
        }
        query.setValue(normalized);
        resubscribe();
    }

    /** Смена фильтра перезапускает запрос к локальной базе. */
    public void setTypeFilter(@Nullable String filter) {
        typeFilter.setValue(filter);
        resubscribe();
    }

    private void combine() {
        String filter = typeFilter.getValue();
        String text = query.getValue();
        List<String> types = typesOf(filter);
        boolean wantEntities = types.isEmpty() ? TextUtils.isEmpty(filter) : true;
        boolean wantRetellings = TextUtils.isEmpty(filter) || "RETELLING".equalsIgnoreCase(filter);

        List<SearchAdapter.Item> out = new ArrayList<>();
        if (!TextUtils.isEmpty(text) && text.trim().length() > 0) {
            if (wantEntities) {
                for (EntityRoom entity : lastEntities) {
                    out.add(new SearchAdapter.Item(SearchAdapter.KIND_ENTITY, entity.id, entity.type,
                            TextUtils.isEmpty(entity.name) ? entity.type : entity.name,
                            snippet(entity.summary, entity.description)));
                }
            }
            if (wantRetellings) {
                for (RetellingRoom retelling : lastRetellings) {
                    out.add(new SearchAdapter.Item(SearchAdapter.KIND_RETELLING, retelling.id, null,
                            retelling.title, snippet(retelling.body)));
                }
            }
        }
        results.setValue(out);
        loading.setValue(false);
    }

    /** Пустой список — «все виды»; {@code null}-фильтр тоже. */
    @NonNull
    private static List<String> typesOf(@Nullable String filter) {
        if (filter == null || filter.trim().isEmpty()
                || "RETELLING".equalsIgnoreCase(filter.trim())) {
            return Collections.emptyList();
        }
        return EntityType.parseList(filter);
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
