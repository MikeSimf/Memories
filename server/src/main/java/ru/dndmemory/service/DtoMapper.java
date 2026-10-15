package ru.dndmemory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.dndmemory.domain.Campaign;
import ru.dndmemory.domain.GameEntity;
import ru.dndmemory.domain.Member;
import ru.dndmemory.domain.Retelling;
import ru.dndmemory.domain.User;
import ru.dndmemory.web.dto.AuthDtos;
import ru.dndmemory.web.dto.CampaignDtos;
import ru.dndmemory.web.dto.EntityDtos;
import ru.dndmemory.web.dto.RetellingDtos;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Собирает DTO из сущностей — единственное место, где форма ответа приводится к docs/api.md. */
@Component
@RequiredArgsConstructor
public class DtoMapper {

    private final ObjectMapper objectMapper;
    private final WikiLinkService wikiLinkService;

    public AuthDtos.UserDto toUser(User user) {
        return AuthDtos.UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .avatarFileId(user.getAvatarFileId())
                .avatarUrl(user.getAvatarFileId() == null ? null : "/api/files/" + user.getAvatarFileId())
                .createdAt(user.getCreatedAt())
                .build();
    }

    public JsonNode parseJson(String json, JsonNode fallback) {
        if (json == null || json.trim().isEmpty()) {
            return fallback;
        }
        try {
            return objectMapper.readTree(json);
        } catch (IOException e) {
            return fallback;
        }
    }

    public List<String> parseTags(String json) {
        List<String> tags = new ArrayList<>();
        JsonNode node = parseJson(json, null);
        if (node != null && node.isArray()) {
            node.forEach(item -> {
                if (item.isTextual()) {
                    tags.add(item.asText());
                }
            });
        }
        return tags;
    }

    public List<EntityDtos.LinkDto> linksOf(Map<String, UUID> nameIndex, String... texts) {
        List<EntityDtos.LinkDto> links = new ArrayList<>();
        for (WikiLink link : wikiLinkService.extract(nameIndex, texts)) {
            links.add(EntityDtos.LinkDto.builder()
                    .targetId(link.getTargetId())
                    .label(link.getLabel())
                    .resolved(link.isResolved())
                    .build());
        }
        return links;
    }

    public EntityDtos.EntityDto toEntity(GameEntity entity, Map<String, UUID> nameIndex,
                                         Map<UUID, String> userNames) {
        return EntityDtos.EntityDto.builder()
                .id(entity.getId())
                .campaignId(entity.getCampaignId())
                .clientId(entity.getClientId())
                .type(entity.getType())
                .name(entity.getName())
                .summary(entity.getSummary())
                .description(entity.getDescription())
                .attrs(parseJson(entity.getAttrs(), objectMapper.createObjectNode()))
                .tags(parseTags(entity.getTags()))
                .imageFileId(entity.getImageFileId())
                .links(linksOf(nameIndex, entity.getSummary(), entity.getDescription()))
                .createdBy(entity.getCreatedBy())
                .createdByName(userNames.get(entity.getCreatedBy()))
                .updatedBy(entity.getUpdatedBy())
                .updatedByName(userNames.get(entity.getUpdatedBy()))
                .revision(entity.getRevision())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .deleted(entity.isDeleted())
                .build();
    }

    public RetellingDtos.RetellingDto toRetelling(Retelling retelling, Map<String, UUID> nameIndex,
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
                .links(linksOf(nameIndex, retelling.getBody()))
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
                                                           long entityCount, long retellingCount) {
        return CampaignDtos.CampaignDetailDto.builder()
                .id(campaign.getId())
                .title(campaign.getTitle())
                .description(campaign.getDescription())
                .ownerUserId(campaign.getOwnerUserId())
                .myRole(member == null ? null : member.getRole())
                .myPermissions(new ArrayList<>(permissions))
                .memberCount(memberCount)
                .entityCount(entityCount)
                .retellingCount(retellingCount)
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
                .memberCount(memberCount)
                .entityCount(entityCount)
                .retellingCount(retellingCount)
                .latestRevision(campaign.getRevision())
                .updatedAt(campaign.getUpdatedAt())
                .build();
    }

    public CampaignDtos.MemberDto toMember(Member member, User user, String linkedEntityName) {
        return CampaignDtos.MemberDto.builder()
                .id(member.getId())
                .userId(member.getUserId())
                .displayName(user == null ? null : user.getDisplayName())
                .email(user == null ? null : user.getEmail())
                .role(member.getRole())
                .linkedEntityId(member.getLinkedEntityId())
                .linkedEntityName(linkedEntityName)
                .status(member.getStatus().name())
                .createdAt(member.getCreatedAt())
                .build();
    }
}
