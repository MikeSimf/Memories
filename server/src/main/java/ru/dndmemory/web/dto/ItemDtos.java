package ru.dndmemory.web.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.UUID;

/** Предмет: владелец как написано в мире и необязательная ссылка на героя или персонажа. */
public final class ItemDtos {

    private ItemDtos() {
    }

    @Getter
    @Setter
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemDto extends ObjectDto {
        /** Тип из справочника item_types. */
        private String itemType;
        private String ownerName;
        private UUID ownerHeroId;
        private String ownerHeroName;
        private UUID ownerCharacterId;
        private String ownerCharacterName;
    }

    @Getter
    @Setter
    public static class CreateItemRequest {
        private UUID id;

        @NotBlank(message = "Укажите название предмета")
        @Size(max = 200, message = "Не длиннее 200 символов")
        private String name;

        private String description;

        private UUID imageFileId;
        private String itemType;

        /** Файлы: фото и документы. Форма совпадает с ответом. */
        private java.util.List<ru.dndmemory.web.dto.AttachmentDto> files;

        @Size(max = 200, message = "Не длиннее 200 символов")
        private String ownerName;

        private UUID ownerHeroId;

        private UUID ownerCharacterId;
    }

    @Getter
    @Setter
    public static class UpdateItemRequest {
        @Size(max = 200, message = "Не длиннее 200 символов")
        private String name;

        private String description;

        private UUID imageFileId;
        private String itemType;

        /** Файлы: фото и документы. Форма совпадает с ответом. */
        private java.util.List<ru.dndmemory.web.dto.AttachmentDto> files;

        @Size(max = 200, message = "Не длиннее 200 символов")
        private String ownerName;

        private UUID ownerHeroId;

        private UUID ownerCharacterId;
    }
}
