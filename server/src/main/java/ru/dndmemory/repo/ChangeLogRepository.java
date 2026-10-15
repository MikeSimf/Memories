package ru.dndmemory.repo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.dndmemory.domain.ChangeLog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChangeLogRepository extends JpaRepository<ChangeLog, UUID> {

    Optional<ChangeLog> findByCampaignIdAndClientChangeId(UUID campaignId, String clientChangeId);

    List<ChangeLog> findAllByCampaignIdAndRevisionGreaterThanOrderByRevisionAsc(
            UUID campaignId, long revision, Pageable pageable);

    List<ChangeLog> findAllByCampaignIdOrderByRevisionDesc(UUID campaignId, Pageable pageable);

    @Query("select min(c.revision) from ChangeLog c where c.campaignId = :campaignId")
    Long findMinRevision(@Param("campaignId") UUID campaignId);

    long countByCampaignId(UUID campaignId);

    @Modifying
    @Query("delete from ChangeLog c where c.createdAt < :before")
    int deleteOlderThan(@Param("before") java.time.Instant before);

    @Modifying
    @Query("delete from ChangeLog c where c.campaignId = :campaignId and c.revision < :revision")
    int deleteBelowRevision(@Param("campaignId") UUID campaignId, @Param("revision") long revision);
}
