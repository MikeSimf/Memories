package ru.dndmemory.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import ru.dndmemory.data.local.entity.MemberRoom;

@Dao
public interface MemberDao {

    @Query("SELECT * FROM members WHERE campaignId = :campaignId "
            + "ORDER BY CASE role WHEN 'OWNER' THEN 0 WHEN 'EDITOR_MASTER' THEN 1 "
            + "WHEN 'EDITOR_HERO' THEN 2 ELSE 3 END, displayName COLLATE NOCASE ASC")
    LiveData<List<MemberRoom>> observeByCampaign(String campaignId);

    @Query("SELECT * FROM members WHERE campaignId = :campaignId")
    List<MemberRoom> findAllNow(String campaignId);

    @Query("SELECT * FROM members WHERE id = :id")
    MemberRoom find(String id);

    @Query("SELECT COUNT(*) FROM members WHERE campaignId = :campaignId AND status = 'ACTIVE'")
    LiveData<Integer> observeActiveCount(String campaignId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(MemberRoom member);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<MemberRoom> members);

    @Query("DELETE FROM members WHERE id = :id")
    void delete(String id);

    @Query("DELETE FROM members WHERE campaignId = :campaignId")
    void clearForCampaign(String campaignId);
}
