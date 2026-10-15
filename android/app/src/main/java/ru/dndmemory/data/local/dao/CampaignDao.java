package ru.dndmemory.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import ru.dndmemory.data.local.entity.CampaignRoom;

@Dao
public interface CampaignDao {

    @Query("SELECT * FROM campaigns WHERE hidden = 0 ORDER BY title COLLATE NOCASE ASC")
    LiveData<List<CampaignRoom>> observeAll();

    @Query("SELECT * FROM campaigns WHERE id = :id")
    LiveData<CampaignRoom> observe(String id);

    @Query("SELECT * FROM campaigns WHERE id = :id")
    CampaignRoom find(String id);

    @Query("SELECT * FROM campaigns WHERE hidden = 0 ORDER BY title COLLATE NOCASE ASC")
    List<CampaignRoom> findAllNow();

    @Query("SELECT COUNT(*) FROM campaigns WHERE hidden = 0")
    LiveData<Integer> observeCount();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(CampaignRoom campaign);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<CampaignRoom> campaigns);

    @Update
    void update(CampaignRoom campaign);

    @Query("DELETE FROM campaigns WHERE id = :id")
    void delete(String id);


    @Query("UPDATE campaigns SET hidden = 1 WHERE id = :id")
    void hide(String id);

    @Query("UPDATE campaigns SET latestRevision = :revision WHERE id = :id")
    void updateRevision(String id, long revision);

    @Query("UPDATE campaigns SET myRole = :role, myPermissions = :permissions WHERE id = :id")
    void updateRoleAndPermissions(String id, String role, List<String> permissions);

    @Query("DELETE FROM campaigns")
    void clear();
}
