package ru.dndmemory.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.dndmemory.domain.Device;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    List<Device> findAllByCampaignIdOrderByCreatedAtDesc(UUID campaignId);

    List<Device> findAllByUserId(UUID userId);

    Optional<Device> findByIdAndCampaignId(UUID id, UUID campaignId);

    long countByCampaignIdAndRevokedFalse(UUID campaignId);
}
