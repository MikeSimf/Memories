package ru.dndmemory.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import ru.dndmemory.data.local.entity.EntityRoom;

@Dao
public interface EntityDao {

    @Query("SELECT * FROM entities WHERE campaignId = :campaignId AND deleted = 0 "
            + "ORDER BY name COLLATE NOCASE ASC")
    LiveData<List<EntityRoom>> observeAll(String campaignId);

    @Query("SELECT * FROM entities WHERE campaignId = :campaignId AND deleted = 0 AND type = :type "
            + "ORDER BY name COLLATE NOCASE ASC")
    LiveData<List<EntityRoom>> observeByType(String campaignId, String type);

    /** Вкладка с несколькими видами объектов: «Персонажи» — {@code HERO,NPC}. */
    @Query("SELECT * FROM entities WHERE campaignId = :campaignId AND deleted = 0 "
            + "AND type IN (:types) ORDER BY name COLLATE NOCASE ASC")
    LiveData<List<EntityRoom>> observeByTypes(String campaignId, List<String> types);

    @Query("SELECT * FROM entities WHERE campaignId = :campaignId AND deleted = 0 "
            + "AND type IN (:types) ORDER BY name COLLATE NOCASE ASC")
    List<EntityRoom> findByTypesNow(String campaignId, List<String> types);

    @Query("SELECT * FROM entities WHERE id = :id")
    LiveData<EntityRoom> observe(String id);

    @Query("SELECT * FROM entities WHERE id = :id")
    EntityRoom find(String id);

    @Query("SELECT * FROM entities WHERE campaignId = :campaignId AND deleted = 0 AND type = :type "
            + "ORDER BY name COLLATE NOCASE ASC")
    List<EntityRoom> findByTypeNow(String campaignId, String type);

    @Query("SELECT * FROM entities WHERE campaignId = :campaignId AND deleted = 0 "
            + "ORDER BY name COLLATE NOCASE ASC")
    List<EntityRoom> findAllNow(String campaignId);

    @Query("SELECT * FROM entities WHERE campaignId = :campaignId AND dirty = 1")
    List<EntityRoom> findDirty(String campaignId);

    @Query("SELECT COUNT(*) FROM entities WHERE campaignId = :campaignId AND dirty = 1")
    LiveData<Integer> observeDirtyCount(String campaignId);

    @Query("SELECT COUNT(*) FROM entities WHERE campaignId = :campaignId AND deleted = 0 AND type = :type")
    LiveData<Integer> observeCountByType(String campaignId, String type);

    /** Офлайн-поиск: та же логика, что у сервера (name/summary/description/tags). */
    @Query("SELECT * FROM entities WHERE campaignId = :campaignId AND deleted = 0 "
            + "AND (name LIKE :pattern OR summary LIKE :pattern OR description LIKE :pattern) "
            + "ORDER BY name COLLATE NOCASE ASC")
    LiveData<List<EntityRoom>> search(String campaignId, String pattern);

    @Query("SELECT * FROM entities WHERE campaignId = :campaignId AND deleted = 0 "
            + "AND type = :type "
            + "AND (name LIKE :pattern OR summary LIKE :pattern OR description LIKE :pattern) "
            + "ORDER BY name COLLATE NOCASE ASC")
    LiveData<List<EntityRoom>> searchByType(String campaignId, String type, String pattern);

    /** Поиск внутри вкладки с несколькими видами: «Персонажи» — {@code HERO,NPC}. */
    @Query("SELECT * FROM entities WHERE campaignId = :campaignId AND deleted = 0 "
            + "AND type IN (:types) "
            + "AND (name LIKE :pattern OR summary LIKE :pattern OR description LIKE :pattern) "
            + "ORDER BY name COLLATE NOCASE ASC")
    LiveData<List<EntityRoom>> searchByTypes(String campaignId, List<String> types, String pattern);

    @Query("SELECT * FROM entities WHERE campaignId = :campaignId AND deleted = 0 "
            + "AND (name LIKE :pattern OR summary LIKE :pattern OR description LIKE :pattern) "
            + "ORDER BY name COLLATE NOCASE ASC")
    List<EntityRoom> searchNow(String campaignId, String pattern);

    /** Разрешение ссылки [[Имя]] без учёта регистра (docs/sync.md, раздел 7). */
    @Query("SELECT * FROM entities WHERE campaignId = :campaignId AND deleted = 0 "
            + "AND name = :name COLLATE NOCASE ORDER BY name COLLATE NOCASE ASC LIMIT 1")
    EntityRoom resolveByName(String campaignId, String name);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(EntityRoom entity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<EntityRoom> entities);

    @Query("UPDATE entities SET dirty = 0, conflictJson = NULL, revision = :revision WHERE id = :id")
    void markClean(String id, long revision);

    @Query("UPDATE entities SET dirty = 0, revision = :revision, updatedAt = :updatedAt, deleted = 1 "
            + "WHERE id = :id")
    void applyTombstone(String id, long revision, String updatedAt);

    @Query("UPDATE entities SET deleted = 1, dirty = 1, updatedAt = :updatedAt WHERE id = :id")
    void markDeleted(String id, String updatedAt);

    @Query("UPDATE entities SET conflictJson = :conflictJson, dirty = 0 WHERE id = :id")
    void setConflict(String id, String conflictJson);

    @Query("UPDATE entities SET conflictJson = NULL WHERE id = :id")
    void clearConflict(String id);

    @Query("DELETE FROM entities WHERE id = :id")
    void delete(String id);

    @Query("DELETE FROM entities WHERE campaignId = :campaignId")
    void clearForCampaign(String campaignId);
}
