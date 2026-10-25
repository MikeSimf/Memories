package ru.dndmemory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.dndmemory.config.AppProperties;
import ru.dndmemory.domain.Campaign;
import ru.dndmemory.domain.Member;
import ru.dndmemory.domain.Retelling;
import ru.dndmemory.domain.User;
import ru.dndmemory.web.dto.AuthDtos;
import ru.dndmemory.web.dto.CampaignDtos;
import ru.dndmemory.web.dto.LinkDto;
import ru.dndmemory.web.dto.RetellingDtos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Собирает DTO профиля, кампании, участника и пересказа — форма ответа из docs/api.md. */
@Component
@RequiredArgsConstructor
public class DtoMapper {

    private final AppProperties properties;

    public AuthDtos.UserDto toUser(User user) {
        return AuthDtos.UserDto.builder()
                .id(user.getId())
                .login(user.getLogin())
                .email(user.getEmail())
                .emailVerified(user.isEmailVerified())
                .displayName(user.getDisplayName())
                .avatarFileId(user.getAvatarFileId())
                .avatarUrl(user.getAvatarFileId() == null ? null : "/api/files/" + user.getAvatarFileId())
                .phone(user.getPhone())
                .hasPassword(user.hasPassword())
                .admin(isAdmin(user))
                .createdAt(user.getCreatedAt())
                .build();
    }

    private boolean isAdmin(User user) {
        if (user.isSuperuser()) {
            return true;
        }
        String admin = properties.getAdminEmail();
        return admin != null && !admin.isEmpty()
                && admin.trim().equalsIgnoreCase(user.getEmail());
    }

    public RetellingDtos.RetellingDto toRetelling(Retelling retelling, List<LinkDto> links,
                                                  Map<UUID, String> userNames, String authorName) {
        return RetellingDtos.RetellingDto.builder()
                .id(retelling.getId())
                .campaignId(retelling.getCampaignId())
                .clientId(retelling.getClientId())
                .title(retelling.getTitle())
                .body(retelling.getBody())
                .authorEntityId(retelling.getAuthorEntityId())
                .authorEntityName(authorName)
                .sessionDate(retelling.getSessionDate())
                .status(retelling.getStatus())
                .links(links)
                .createdBy(retelling.getCreatedBy())
                .createdByName(userNames.get(retelling.getCreatedBy()))
                .updatedBy(retelling.getUpdatedBy())
                .revision(retelling.getRevision())
                .createdAt(retelling.getCreatedAt())
                .updatedAt(retelling.getUpdatedAt())
                .deleted(retelling.isDeleted())
                .build();
    }

    public CampaignDtos.CampaignDetailDto toCampaignDetail(Campaign campaign, Member member,
                                                           Collection<String> permissions, long memberCount,
                                                           long entityCount, long retellingCount,
                                                           List<CampaignDtos.MemberDto> members,
                                                           List<CampaignDtos.RetellingBriefDto> retellings) {
        return CampaignDtos.CampaignDetailDto.builder()
                .id(campaign.getId())
                .title(campaign.getTitle())
                .description(campaign.getDescription())
                .ownerUserId(campaign.getOwnerUserId())
                .myRole(member == null ? null : member.getRole())
                .myLinkedHeroId(member == null ? null : member.getLinkedHeroId())
                .myPermissions(new ArrayList<>(permissions))
                .memberCount(memberCount)
                .entityCount(entityCount)
                .retellingCount(retellingCount)
                .members(members)
                .retellings(retellings)
                .latestRevision(campaign.getRevision())
                .createdAt(campaign.getCreatedAt())
                .updatedAt(campaign.getUpdatedAt())
                .build();
    }

    public CampaignDtos.CampaignSummaryDto toCampaignSummary(Campaign campaign, Member member,
                                                             long memberCount, long entityCount,
                                                             long retellingCount) {
        return CampaignDtos.CampaignSummaryDto.builder()
                .id(campaign.getId())
                .title(campaign.getTitle())
                .description(campaign.getDescription())
                .myRole(member == null ? null : member.getRole())
                .myLinkedHeroId(member == null ? null : member.getLinkedHeroId())
                .memberCount(memberCount)
                .entityCount(entityCount)
                .retellingCount(retellingCount)
                .latestRevision(campaign.getRevision())
                .updatedAt(campaign.getUpdatedAt())
                .build();
    }

    public CampaignDtos.MemberDto toMember(Member member, User user, String linkedHeroName) {
        return CampaignDtos.MemberDto.builder()
                .id(member.getId())
                .userId(member.getUserId())
                .displayName(user == null ? null : user.getDisplayName())
                .email(user == null ? null : user.getEmail())
                .role(member.getRole())
                .linkedHeroId(member.getLinkedHeroId())
                .linkedHeroName(linkedHeroName)
                .status(member.getStatus().name())
                .createdAt(member.getCreatedAt())
                .build();
    }
}
