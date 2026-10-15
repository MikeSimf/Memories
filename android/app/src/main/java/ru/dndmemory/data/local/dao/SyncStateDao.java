package ru.dndmemory.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import ru.dndmemory.data.local.entity.SyncStateRoom;

@Dao
public interface SyncStateDao {

    @Query("SELECT * FROM sync_state WHERE campaignId = :campaignId")
    SyncStateRoom find(String campaignId);

    @Query("SELECT * FROM sync_state WHERE campaignId = :campaignId")
    LiveData<SyncStateRoom> observe(String campaignId);

    @Query("SELECT * FROM sync_state")
    List<SyncStateRoom> all();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(SyncStateRoom state);

    @Query("UPDATE sync_state SET lastPulledRevision = :revision, latestRevision = :revision "
            + "WHERE campaignId = :campaignId")
    void setPulledRevision(String campaignId, long revision);

    @Query("UPDATE sync_state SET latestRevision = :revision WHERE campaignId = :campaignId")
    void setLatestRevision(String campaignId, long revision);

    @Query("UPDATE sync_state SET lastSyncAt = :at, lastError = NULL WHERE campaignId = :campaignId")
    void markSynced(String campaignId, String at);

    @Query("UPDATE sync_state SET lastError = :error WHERE campaignId = :campaignId")
    void markError(String campaignId, String error);

    @Query("DELETE FROM sync_state WHERE campaignId = :campaignId")
    void clearForCampaign(String campaignId);
}
