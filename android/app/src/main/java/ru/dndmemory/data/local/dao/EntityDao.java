package ru.dndmemory.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import ru.dndmemory.data.local.entity.EntityRoom;

/**
 * Объекты кампании: одна таблица {@code entities} с дискриминатором {@code kind}
 * (docs/redesign-v2.md, раздел 2). Запросы вкладок отличаются только фильтром
 * по классу, поэтому отдельные DAO на каждый класс не нужны.
 */
@Dao
public interface EntityDao {

    @Query("SELECT * FROM entities WHERE campaignId = :campaignId AND deleted = 0 "
            + "ORDER BY name COLLATE NOCASE ASC")
    LiveData<List<EntityRoom>> observeAll(String campaignId);

    @Query("SELECT * FROM entities WHERE campaignId = :campaignId AND deleted = 0 AND kind = :kind "
            + "ORDER BY name COLLATE NOCASE ASC")
    LiveData<List<EntityRoom>> observeByKind(String campaignId, String kind);

    /** Вкладка с несколькими классами: «Персонажи» — {@code HERO,CHARACTER}. */
    @Query("SELECT * FROM entities WHERE campaignId = :campaignId AND deleted = 0 "
            + "AND kind IN (:kinds) ORDER BY name COLLATE NOCASE ASC")
    LiveData<List<EntityRoom>> observeByKinds(String campaignId, List<String> kinds);

    @Query("SELECT * FROM entities WHERE campaignId = :campaignId AND deleted = 0 "
            + "AND kind IN (:kinds) ORDER BY name COLLATE NOCASE ASC")
    List<EntityRoom> findByKindsNow(String campaignId, List<String> kinds);

    @Query("SELECT * FROM entities WHERE campaignId = :campaignId AND deleted = 0 AND kind = :kind "
            + "ORDER BY name COLLATE NOCASE ASC")
    List<EntityRoom> findByKindNow(String campaignId, String kind);

    @Query("SELECT * FROM entities WHERE campaignId = :campaignId AND deleted = 0 "
            + "ORDER BY name COLLATE NOCASE ASC")
    List<EntityRoom> findAllNow(String campaignId);

    @Query("SELECT * FROM entities WHERE id = :id")
    LiveData<EntityRoom> observe(String id);

    @Query("SELECT * FROM entities WHERE id = :id")
    EntityRoom find(String id);

    /**
     * Офлайн-поиск по локальной базе (docs/api.md, раздел 8): по {@code name}
     * и {@code description}; у персонажа дополнительно по {@code race}
     * и {@code shortDescription}.
     */
    @Query("SELECT * FROM entities WHERE campaignId = :campaignId AND deleted = 0 "
            + "AND (name LIKE :pattern OR description LIKE :pattern "
            + "     OR (kind = 'CHARACTER' AND (race LIKE :pattern OR shortDescription LIKE :pattern))) "
            + "ORDER BY name COLLATE NOCASE ASC")
    LiveData<List<EntityRoom>> search(String campaignId, String pattern);

    /** Поиск внутри вкладки: та же логика, но только по её классам. */
    @Query("SELECT * FROM entities WHERE campaignId = :campaignId AND deleted = 0 "
            + "AND kind IN (:kinds) "
            + "AND (name LIKE :pattern OR description LIKE :pattern "
            + "     OR (kind = 'CHARACTER' AND (race LIKE :pattern OR shortDescription LIKE :pattern))) "
            + "ORDER BY name COLLATE NOCASE ASC")
    LiveData<List<EntityRoom>> searchByKinds(String campaignId, List<String> kinds, String pattern);

    /** Разрешение ссылки [[Имя]] без учёта регистра (docs/sync.md, раздел 8). */
    @Query("SELECT * FROM entities WHERE campaignId = :campaignId AND deleted = 0 "
            + "AND name = :name COLLATE NOCASE ORDER BY name COLLATE NOCASE ASC LIMIT 1")
    EntityRoom resolveByName(String campaignId, String name);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(EntityRoom entity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<EntityRoom> entities);

    @Query("UPDATE entities SET dirty = 0, conflictJson = NULL, revision = :revision WHERE id = :id")
    void markClean(String id, long revision);

    /** Сервер нашёл объект по ключу сопоставления и вернул свой id (docs/sync.md, 3.1). */
    @Query("UPDATE entities SET id = :newId WHERE id = :oldId")
    void reassignId(String oldId, String newId);

    @Query("UPDATE entities SET dirty = 0, revision = :revision, updatedAt = :updatedAt, deleted = 1 "
            + "WHERE id = :id")
    void applyTombstone(String id, long revision, String updatedAt);

    @Query("UPDATE entities SET deleted = 1, dirty = 1, updatedAt = :updatedAt WHERE id = :id")
    void markDeleted(String id, String updatedAt);

    @Query("UPDATE entities SET conflictJson = :conflictJson, dirty = 0 WHERE id = :id")
    void setConflict(String id, String conflictJson);

    @Query("DELETE FROM entities WHERE id = :id")
    void delete(String id);

    @Query("DELETE FROM entities WHERE campaignId = :campaignId")
    void clearForCampaign(String campaignId);
}
