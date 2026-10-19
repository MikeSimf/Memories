package ru.dndmemory.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.dndmemory.web.dto.AttachmentDto;
import ru.dndmemory.web.dto.LinkDto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Фото-материалы кампании: карта, портрет, письмо. */
public final class MaterialDtos {

    private MaterialDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MaterialDto {
        private UUID id;
        private UUID campaignId;
        private String name;
        private String description;
        private UUID imageFileId;
        private UUID avatarFileId;
        private List<AttachmentDto> files;
        private List<LinkDto> links;
        private UUID createdBy;
        private String createdByName;
        private UUID updatedBy;
        private String updatedByName;
        private Long revision;
        private Instant createdAt;
        private Instant updatedAt;
        private boolean deleted;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateMaterialRequest {
        private UUID id;

        @NotBlank(message = "Укажите название материала")
        @Size(max = 200, message = "Название не длиннее 200 символов")
        private String name;

        private String description;
        private UUID imageFileId;
        private UUID avatarFileId;

        /** Файлы: фото и документы. Форма совпадает с ответом. */
        private java.util.List<ru.dndmemory.web.dto.AttachmentDto> files;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateMaterialRequest {
        @Size(max = 200, message = "Название не длиннее 200 символов")
        private String name;

        private String description;
        private UUID imageFileId;
        private UUID avatarFileId;

        /** Файлы: фото и документы. Форма совпадает с ответом. */
        private java.util.List<ru.dndmemory.web.dto.AttachmentDto> files;
    }
}
