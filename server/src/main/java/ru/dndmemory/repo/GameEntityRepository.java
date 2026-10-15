package ru.dndmemory.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.dndmemory.domain.EntityType;
import ru.dndmemory.domain.GameEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameEntityRepository extends JpaRepository<GameEntity, UUID> {

    Optional<GameEntity> findByIdAndCampaignId(UUID id, UUID campaignId);

    Optional<GameEntity> findByCampaignIdAndClientId(UUID campaignId, UUID clientId);

    List<GameEntity> findAllByCampaignIdAndDeletedFalseOrderByNameAsc(UUID campaignId);

    List<GameEntity> findAllByCampaignIdAndDeletedFalseAndTypeOrderByNameAsc(UUID campaignId, EntityType type);

    List<GameEntity> findAllByCampaignIdAndIdIn(UUID campaignId, Collection<UUID> ids);

    Page<GameEntity> findAllByCampaignIdAndDeletedFalse(UUID campaignId, Pageable pageable);

    long countByCampaignIdAndDeletedFalse(UUID campaignId);
}
