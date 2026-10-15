package ru.dndmemory.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.dndmemory.domain.Invite;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InviteRepository extends JpaRepository<Invite, UUID> {

    Optional<Invite> findByToken(String token);

    List<Invite> findAllByCampaignIdOrderByCreatedAtDesc(UUID campaignId);

    Optional<Invite> findByIdAndCampaignId(UUID id, UUID campaignId);

    @Modifying
    @Query("update Invite i set i.revoked = true where i.expiresAt < :now and i.revoked = false")
    int revokeExpired(@Param("now") Instant now);
}
