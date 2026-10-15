package ru.dndmemory.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.dndmemory.domain.Member;
import ru.dndmemory.domain.MemberRole;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberRepository extends JpaRepository<Member, UUID> {

    Optional<Member> findByCampaignIdAndUserId(UUID campaignId, UUID userId);

    Optional<Member> findByIdAndCampaignId(UUID id, UUID campaignId);

    List<Member> findAllByCampaignIdOrderByCreatedAtAsc(UUID campaignId);

    List<Member> findAllByUserId(UUID userId);

    long countByCampaignIdAndRole(UUID campaignId, MemberRole role);

    long countByCampaignId(UUID campaignId);
}
