package ru.dndmemory.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.dndmemory.domain.Retelling;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RetellingRepository extends JpaRepository<Retelling, UUID> {

    Optional<Retelling> findByIdAndCampaignId(UUID id, UUID campaignId);

    Optional<Retelling> findByCampaignIdAndClientId(UUID campaignId, UUID clientId);

    List<Retelling> findAllByCampaignIdAndDeletedFalseOrderByCreatedAtDesc(UUID campaignId);

    Page<Retelling> findAllByCampaignIdAndDeletedFalse(UUID campaignId, Pageable pageable);

    long countByCampaignIdAndDeletedFalse(UUID campaignId);
}
