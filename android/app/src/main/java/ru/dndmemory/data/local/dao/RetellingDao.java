package ru.dndmemory.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import ru.dndmemory.data.local.entity.RetellingRoom;

@Dao
public interface RetellingDao {

    @Query("SELECT * FROM retellings WHERE campaignId = :campaignId AND deleted = 0 "
            + "ORDER BY sessionDate DESC, updatedAt DESC")
    LiveData<List<RetellingRoom>> observeByCampaign(String campaignId);

    @Query("SELECT * FROM retellings WHERE id = :id")
    LiveData<RetellingRoom> observe(String id);

    @Query("SELECT * FROM retellings WHERE id = :id")
    RetellingRoom find(String id);

    @Query("SELECT * FROM retellings WHERE campaignId = :campaignId AND deleted = 0 "
            + "ORDER BY sessionDate DESC, updatedAt DESC")
    List<RetellingRoom> findAllNow(String campaignId);

    @Query("SELECT * FROM retellings WHERE campaignId = :campaignId AND dirty = 1")
    List<RetellingRoom> findDirty(String campaignId);

    @Query("SELECT COUNT(*) FROM retellings WHERE campaignId = :campaignId AND dirty = 1")
    LiveData<Integer> observeDirtyCount(String campaignId);

    @Query("SELECT * FROM retellings WHERE campaignId = :campaignId AND deleted = 0 "
            + "AND (title LIKE :pattern OR body LIKE :pattern) "
            + "ORDER BY sessionDate DESC, updatedAt DESC")
    LiveData<List<RetellingRoom>> search(String campaignId, String pattern);

    @Query("SELECT * FROM retellings WHERE campaignId = :campaignId AND deleted = 0 "
            + "AND (title LIKE :pattern OR body LIKE :pattern) "
            + "ORDER BY sessionDate DESC, updatedAt DESC")
    List<RetellingRoom> searchNow(String campaignId, String pattern);

    @Query("SELECT * FROM retellings WHERE campaignId = :campaignId AND deleted = 0 "
            + "AND authorEntityId = :authorEntityId "
            + "ORDER BY sessionDate DESC, updatedAt DESC")
    LiveData<List<RetellingRoom>> observeByAuthor(String campaignId, String authorEntityId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(RetellingRoom retelling);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<RetellingRoom> retellings);

    @Query("UPDATE retellings SET dirty = 0, conflictJson = NULL, revision = :revision WHERE id = :id")
    void markClean(String id, long revision);

    @Query("UPDATE retellings SET dirty = 0, revision = :revision, updatedAt = :updatedAt, deleted = 1 "
            + "WHERE id = :id")
    void applyTombstone(String id, long revision, String updatedAt);

    @Query("UPDATE retellings SET deleted = 1, dirty = 1, updatedAt = :updatedAt WHERE id = :id")
    void markDeleted(String id, String updatedAt);

    @Query("UPDATE retellings SET conflictJson = :conflictJson, dirty = 0 WHERE id = :id")
    void setConflict(String id, String conflictJson);

    @Query("DELETE FROM retellings WHERE id = :id")
    void delete(String id);

    @Query("DELETE FROM retellings WHERE campaignId = :campaignId")
    void clearForCampaign(String campaignId);
}
