package ru.dndmemory.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ru.dndmemory.data.local.entity.EntityRoom;

/**
 * Указатель имён объектов кампании для разметки {@code [[Имя]]}.
 * Строится заранее в фоне: Room запрещает запросы в главном потоке, а отрисовка
 * текста со ссылками происходит именно там.
 */
public final class NameIndex {

    private final Map<String, String> nameToId = new HashMap<>();
    private final Map<String, String> idToName = new HashMap<>();

    public static NameIndex of(@Nullable List<EntityRoom> entities) {
        NameIndex index = new NameIndex();
        if (entities == null) {
            return index;
        }
        for (EntityRoom entity : entities) {
            if (entity == null || entity.name == null || entity.name.isEmpty()) {
                continue;
            }
            index.nameToId.put(entity.name.toLowerCase(Locale.ROOT), entity.id);
            index.idToName.put(entity.id, entity.name);
        }
        return index;
    }

    @Nullable
    public String idFor(@Nullable String name) {
        if (name == null) {
            return null;
        }
        return nameToId.get(name.toLowerCase(Locale.ROOT));
    }

    @Nullable
    public String nameFor(@Nullable String id) {
        if (id == null) {
            return null;
        }
        return idToName.get(id);
    }

    @NonNull
    public List<String> names() {
        return new java.util.ArrayList<>(idToName.values());
    }

    public boolean isEmpty() {
        return nameToId.isEmpty();
    }
}
