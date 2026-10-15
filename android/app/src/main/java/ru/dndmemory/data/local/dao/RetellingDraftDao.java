package ru.dndmemory.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import ru.dndmemory.data.local.entity.RetellingDraftRoom;

@Dao
public interface RetellingDraftDao {

    @Query("SELECT * FROM retelling_drafts WHERE campaignId = :campaignId "
            + "ORDER BY updatedAt DESC")
    LiveData<List<RetellingDraftRoom>> observeByCampaign(String campaignId);

    @Query("SELECT * FROM retelling_drafts WHERE id = :id")
    LiveData<RetellingDraftRoom> observe(String id);

    @Query("SELECT * FROM retelling_drafts WHERE id = :id")
    RetellingDraftRoom find(String id);

    @Query("SELECT * FROM retelling_drafts WHERE remoteId = :remoteId LIMIT 1")
    RetellingDraftRoom findByRemoteId(String remoteId);

    @Query("SELECT * FROM retelling_drafts WHERE campaignId = :campaignId")
    List<RetellingDraftRoom> findAllNow(String campaignId);

    @Query("SELECT COUNT(*) FROM retelling_drafts WHERE campaignId = :campaignId")
    LiveData<Integer> observeCount(String campaignId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(RetellingDraftRoom draft);

    @Query("DELETE FROM retelling_drafts WHERE id = :id")
    void delete(String id);

    @Query("DELETE FROM retelling_drafts WHERE remoteId = :remoteId")
    void deleteByRemoteId(String remoteId);

    @Query("DELETE FROM retelling_drafts WHERE campaignId = :campaignId")
    void clearForCampaign(String campaignId);
}
