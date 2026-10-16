package ru.dndmemory.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.dndmemory.domain.RetellingStatus;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class RetellingDtos {

    private RetellingDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetellingDto {
        private UUID id;
        private UUID campaignId;
        private UUID clientId;
        private String title;
        private String body;
        private UUID authorEntityId;
        private String authorEntityName;
        private LocalDate sessionDate;
        private RetellingStatus status;
        private List<LinkDto> links;
        private UUID createdBy;
        private String createdByName;
        private UUID updatedBy;
        private long revision;
        private Instant createdAt;
        private Instant updatedAt;
        private boolean deleted;
    }

    @Data
    public static class CreateRetellingRequest {
        private UUID id;

        private UUID clientId;

        @NotBlank(message = "Укажите заголовок")
        @Size(max = 200, message = "Не длиннее 200 символов")
        private String title;

        private String body;

        /** Герой или персонаж, от лица которого ведётся пересказ. */
        private UUID authorEntityId;

        private LocalDate sessionDate;

        /** Разрешён только PUBLISHED (docs/api.md, раздел 7). */
        private RetellingStatus status;
    }

    @Data
    public static class UpdateRetellingRequest {
        @Size(max = 200, message = "Не длиннее 200 символов")
        private String title;

        private String body;

        private UUID authorEntityId;

        private LocalDate sessionDate;

        private RetellingStatus status;
    }
}
