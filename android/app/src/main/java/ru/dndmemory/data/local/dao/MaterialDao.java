package ru.dndmemory.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import ru.dndmemory.data.local.entity.MaterialRoom;

@Dao
public interface MaterialDao {

    @Query("SELECT * FROM materials WHERE campaignId = :campaignId AND deleted = 0 ORDER BY name COLLATE NOCASE")
    LiveData<List<MaterialRoom>> observe(String campaignId);

    @Query("SELECT * FROM materials WHERE campaignId = :campaignId AND deleted = 0 ORDER BY name COLLATE NOCASE")
    List<MaterialRoom> findAllNow(String campaignId);

    @Query("SELECT * FROM materials WHERE id = :id")
    MaterialRoom find(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(MaterialRoom room);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<MaterialRoom> rooms);

    @Query("UPDATE materials SET dirty = 0, revision = :revision WHERE id = :id")
    void markClean(String id, long revision);

    @Query("UPDATE materials SET deleted = 1, dirty = 1, updatedAt = :updatedAt WHERE id = :id")
    void markDeleted(String id, String updatedAt);

    @Query("UPDATE materials SET deleted = 1, dirty = 0, revision = :revision, updatedAt = :updatedAt WHERE id = :id")
    void applyTombstone(String id, long revision, String updatedAt);

    @Query("DELETE FROM materials WHERE campaignId = :campaignId")
    void clearForCampaign(String campaignId);
}
