package ru.dndmemory.repo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.dndmemory.domain.SyncConflict;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SyncConflictRepository extends JpaRepository<SyncConflict, UUID> {

    List<SyncConflict> findAllByCampaignIdOrderByCreatedAtDesc(UUID campaignId, Pageable pageable);

    long countByCampaignId(UUID campaignId);

    @Modifying
    @Query("delete from SyncConflict c where c.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
