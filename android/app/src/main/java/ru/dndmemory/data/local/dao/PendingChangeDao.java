package ru.dndmemory.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import ru.dndmemory.data.local.entity.PendingChangeRoom;

@Dao
public interface PendingChangeDao {

    /** Строго в порядке возникновения — так требует push (docs/sync.md, раздел 3). */
    @Query("SELECT * FROM pending_changes WHERE campaignId = :campaignId ORDER BY seq ASC")
    List<PendingChangeRoom> list(String campaignId);

    @Query("SELECT COUNT(*) FROM pending_changes WHERE campaignId = :campaignId")
    int count(String campaignId);

    @Query("SELECT COUNT(*) FROM pending_changes WHERE campaignId = :campaignId")
    LiveData<Integer> observeCount(String campaignId);

    @Query("SELECT * FROM pending_changes WHERE objectId = :objectId ORDER BY seq ASC")
    List<PendingChangeRoom> forObject(String objectId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(PendingChangeRoom change);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<PendingChangeRoom> changes);

    @Delete
    void delete(PendingChangeRoom change);

    @Query("DELETE FROM pending_changes WHERE clientChangeId = :clientChangeId")
    void deleteById(String clientChangeId);

    /** Схлопывает очередь по объекту, оставляя только последнюю правку. */
    @Query("DELETE FROM pending_changes WHERE objectId = :objectId AND clientChangeId <> :keepClientChangeId")
    void deleteOlderForObject(String objectId, String keepClientChangeId);

    @Query("UPDATE pending_changes SET attempts = attempts + 1 WHERE clientChangeId = :clientChangeId")
    void bumpAttempts(String clientChangeId);

    @Query("DELETE FROM pending_changes WHERE campaignId = :campaignId")
    void clearForCampaign(String campaignId);
}
