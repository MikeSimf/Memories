package ru.dndmemory.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.dndmemory.domain.EntityType;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EntityDtos {

    private EntityDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinkDto {
        private UUID targetId;
        private String label;
        private boolean resolved;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EntityDto {
        private UUID id;
        private UUID campaignId;
        private UUID clientId;
        private EntityType type;
        private String name;
        private String summary;
        private String description;
        private JsonNode attrs;
        private List<String> tags;
        private UUID imageFileId;
        private List<LinkDto> links;
        private UUID createdBy;
        private String createdByName;
        private UUID updatedBy;
        private String updatedByName;
        private long revision;
        private Instant createdAt;
        private Instant updatedAt;
        private boolean deleted;
    }

    @Data
    public static class CreateEntityRequest {
        /** Клиент генерирует идентификатор заранее — офлайн-создание не ждёт сети. */
        private UUID id;

        private UUID clientId;

        @NotNull(message = "Укажите тип объекта")
        private EntityType type;

        @NotBlank(message = "Укажите название")
        @Size(max = 200, message = "Не длиннее 200 символов")
        private String name;

        private String summary;

        private String description;

        private JsonNode attrs;

        private List<String> tags;

        private UUID imageFileId;
    }

    @Data
    public static class UpdateEntityRequest {
        @Size(max = 200, message = "Не длиннее 200 символов")
        private String name;

        private String summary;

        private String description;

        private JsonNode attrs;

        private List<String> tags;

        private UUID imageFileId;

        private EntityType type;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EntityListDto {
        private List<EntityDto> items;
        private long total;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BacklinkDto {
        private UUID id;
        private EntityType type;
        private String name;
        private String snippet;
    }
}
