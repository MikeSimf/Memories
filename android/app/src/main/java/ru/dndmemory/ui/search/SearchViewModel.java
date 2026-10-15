package ru.dndmemory.ui.search;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
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

        LiveData<List<EntityRoom>> entitySource = Transformations.switchMap(query, q ->
                entities.search(campaignId, EntityType.from(typeFilter.getValue()), q));
        results.addSource(entitySource, list -> {
            lastEntities = list == null ? Collections.emptyList() : list;
            combine();
        });

        LiveData<List<RetellingRoom>> retellingSource = Transformations.switchMap(query, q ->
                retellings.search(campaignId, q));
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
        query.setValue(value == null ? "" : value);
    }

    /** Смена фильтра перезапускает запрос к локальной базе. */
    public void setTypeFilter(@Nullable String filter) {
        typeFilter.setValue(filter);
        query.setValue(query.getValue());
    }

    private void combine() {
        String filter = typeFilter.getValue();
        String text = query.getValue();
        EntityType entityType = EntityType.from(filter);
        boolean wantEntities = TextUtils.isEmpty(filter) || entityType != null;
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
