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

        /** Полное состояние объекта (для DELETE достаточно id/updatedAt). */
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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SnapshotDto {
        private long latestRevision;
        private String nextCursor;
        private CampaignDtos.CampaignDetailDto campaign;
        private List<EntityDtos.EntityDto> entities;
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
        private long entityCount;
        private long retellingCount;
        private long memberCount;
        private long pendingConflicts;
    }
}
