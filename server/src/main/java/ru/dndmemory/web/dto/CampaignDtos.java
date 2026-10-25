package ru.dndmemory.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.dndmemory.domain.MemberRole;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CampaignDtos {

    private CampaignDtos() {
    }

    @Data
    public static class CreateCampaignRequest {
        @NotBlank(message = "Укажите название кампании")
        @Size(max = 200, message = "Не длиннее 200 символов")
        private String title;

        private String description;
    }

    @Data
    public static class UpdateCampaignRequest {
        @Size(max = 200, message = "Не длиннее 200 символов")
        private String title;

        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CampaignSummaryDto {
        private UUID id;
        private String title;
        private String description;
        private MemberRole myRole;
        /** Герой, привязанный к текущему участнику. */
        private UUID myLinkedHeroId;
        private long memberCount;
        /** Сумма объектов всех четырёх классов. */
        private long entityCount;
        private long retellingCount;
        private long latestRevision;
        private Instant updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CampaignDetailDto {
        private UUID id;
        private String title;
        private String description;
        private UUID ownerUserId;
        private MemberRole myRole;
        /** Герой, привязанный к текущему участнику. */
        private UUID myLinkedHeroId;
        private List<String> myPermissions;
        private long memberCount;
        /** Сумма героев, персонажей, локаций и предметов. */
        private long entityCount;
        private long retellingCount;
        private long latestRevision;
        /** Краткий список участников — чтобы карточка кампании открывалась одним запросом. */
        private List<MemberDto> members;
        /** Краткий список пересказов рядом со счётчиками. */
        private List<RetellingBriefDto> retellings;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetellingBriefDto {
        private UUID id;
        private String title;
        private LocalDate sessionDate;
        private long revision;
        private Instant updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberDto {
        private UUID id;
        private UUID userId;
        private String displayName;
        private String email;
        private MemberRole role;
        /** Герой, который закреплён за участником (герой — отдельная таблица). */
        private UUID linkedHeroId;
        private String linkedHeroName;
        private String status;
        private Instant createdAt;
    }

    @Data
    public static class AddMemberRequest {
        @NotBlank(message = "Укажите почту")
        @Email(message = "Некорректная почта")
        private String email;

        @NotNull(message = "Укажите роль")
        private MemberRole role;

        private UUID linkedHeroId;
    }

    @Data
    public static class UpdateMemberRequest {
        private MemberRole role;

        private UUID linkedHeroId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddMemberResponse {
        private MemberDto member;
        private InviteDtos.InviteDto invite;
        private String note;
    }
}
