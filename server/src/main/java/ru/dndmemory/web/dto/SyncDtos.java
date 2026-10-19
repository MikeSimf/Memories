package ru.dndmemory.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.dndmemory.domain.ChangeOp;
import ru.dndmemory.domain.ObjectType;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SyncDtos {

    private SyncDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PushChangeDto {
        @NotBlank(message = "Не передан clientChangeId")
        private String clientChangeId;

        @NotNull(message = "Не передан objectType")
        private ObjectType objectType;

        @NotNull(message = "Не передан objectId")
        private UUID objectId;

        @NotNull(message = "Не передана операция")
        private ChangeOp op;

        private Long baseRevision;

        /** Полное состояние объекта в форме DTO своего класса (для DELETE достаточно id/updatedAt). */
        private JsonNode payload;
    }

    @Data
    public static class PushRequest {
        private String clientId;

        @NotNull(message = "Список изменений обязателен")
        private List<PushChangeDto> changes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConflictInfoDto {
        /** matched_by_name, matched_by_name_and_race, stale_base, client_wins, forbidden, ... */
        private String reason;
        private Long serverRevision;
        private JsonNode server;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PushResultDto {
        private String clientChangeId;
        private UUID objectId;
        private Long revision;
        /** APPLIED, CONFLICT_RESOLVED или REJECTED. */
        private String status;
        private ConflictInfoDto conflict;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PushResponse {
        private List<PushResultDto> results;
        private long latestRevision;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangeDto {
        private long revision;
        private ObjectType objectType;
        private UUID objectId;
        private ChangeOp op;
        private JsonNode payload;
        private UUID actorUserId;
        private String clientChangeId;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PullResponse {
        private long since;
        private long latestRevision;
        private boolean hasMore;
        private List<ChangeDto> changes;
    }

    /**
     * Полная выгрузка кампании. Страницы идут по порядку классов: герои, персонажи,
     * локации, предметы, пересказы. {@code nextCursor} — позиция следующей страницы.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SnapshotDto {
        private long latestRevision;
        private String nextCursor;
        private CampaignDtos.CampaignDetailDto campaign;
        private List<HeroDtos.HeroDto> heroes;
        private List<CharacterDtos.CharacterDto> characters;
        private List<LocationDtos.LocationDto> locations;
        private List<ItemDtos.ItemDto> items;
        private List<MaterialDtos.MaterialDto> materials;
        private List<RetellingDtos.RetellingDto> retellings;
        private List<CampaignDtos.MemberDto> members;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncStateDto {
        private long latestRevision;
        private Long minRetainedRevision;
        /** Сумма объектов всех четырёх классов. */
        private long entityCount;
        private long heroCount;
        private long characterCount;
        private long locationCount;
        private long itemCount;
        private long retellingCount;
        private long memberCount;
        private long pendingConflicts;
    }
}
