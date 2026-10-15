package ru.dndmemory.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ru.dndmemory.data.model.EntityLink;

/**
 * Объект кампании: герой, НПС, локация или предмет.
 * Все четыре типа живут в одной таблице, различия — в {@code attrs}
 * (docs/architecture.md, раздел 2).
 */
@Entity(
        tableName = "entities",
        indices = {
                @Index(value = {"campaignId"}),
                @Index(value = {"campaignId", "type"}),
                @Index(value = {"campaignId", "name"})
        }
)
public class EntityRoom {

    @PrimaryKey
    @NonNull
    public String id = "";

    public String campaignId;

    /** UUID создания: сервер использует его для идемпотентности. */
    public String clientId;

    /** HERO | NPC | LOCATION | ITEM */
    public String type;

    public String name;
    public String summary;
    public String description;

    public Map<String, String> attrs = new HashMap<>();
    public List<String> tags = new ArrayList<>();
    public List<EntityLink> links = new ArrayList<>();

    public String imageFileId;

    public String createdBy;
    public String updatedBy;
    public long revision;
    public String createdAt;
    public String updatedAt;

    /** Надгробие: объект удалён, но запись нужна для синхронизации. */
    public boolean deleted;

    /** Есть неотправленные локальные правки. */
    public boolean dirty;

    /** JSON расхождения с сервером ({@link ru.dndmemory.data.model.ConflictInfo}). */
    public String conflictJson;

    public EntityRoom() {
    }

    public boolean hasConflict() {
        return conflictJson != null && !conflictJson.isEmpty();
    }

    public String attr(String key) {
        return attrs == null ? null : attrs.get(key);
    }
}
