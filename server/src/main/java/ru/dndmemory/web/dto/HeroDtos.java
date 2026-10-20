package ru.dndmemory.web.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Герой партии: раса, класс, игрок и список предметов в котомке. */
public final class HeroDtos {

    private HeroDtos() {
    }

    @Getter
    @Setter
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HeroItemDto {
        private UUID id;
        private String name;
        /** Заполнен, если название нашлось среди предметов кампании. */
        private UUID itemId;
        private Integer quantity;
        private String notes;
    }

    @Getter
    @Setter
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HeroDto extends ObjectDto {
        private String race;
        private String klass;
        private String gender;
        private UUID playerUserId;
        private String playerName;
        private List<HeroItemDto> items;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HeroItemRequest {
        @NotBlank(message = "Укажите название предмета")
        @Size(max = 200, message = "Не длиннее 200 символов")
        private String name;

        private UUID itemId;

        private Integer quantity;

        private String notes;
    }

    @Getter
    @Setter
    public static class CreateHeroRequest {
        /** Клиент может задать идентификатор заранее — повторная отправка не создаст дубль. */
        private UUID id;

        @NotBlank(message = "Укажите имя героя")
        @Size(max = 200, message = "Не длиннее 200 символов")
        private String name;

        private String description;

        private UUID imageFileId;
        private UUID avatarFileId;

        /** Файлы: фото и документы. Форма совпадает с ответом. */
        private java.util.List<ru.dndmemory.web.dto.AttachmentDto> files;

        @Size(max = 120, message = "Не длиннее 120 символов")
        private String race;

        @Size(max = 120, message = "Не длиннее 120 символов")
        private String klass;

        /** female | male | unknown. */
        private String gender;

        private UUID playerUserId;

        private List<HeroItemRequest> items;
    }

    @Getter
    @Setter
    public static class UpdateHeroRequest {
        @Size(max = 200, message = "Не длиннее 200 символов")
        private String name;

        private String description;

        private UUID imageFileId;
        private UUID avatarFileId;

        /** Файлы: фото и документы. Форма совпадает с ответом. */
        private java.util.List<ru.dndmemory.web.dto.AttachmentDto> files;

        @Size(max = 120, message = "Не длиннее 120 символов")
        private String race;

        @Size(max = 120, message = "Не длиннее 120 символов")
        private String klass;

        private String gender;

        private UUID playerUserId;

        /** Если передан — список предметов заменяется целиком. */
        private List<HeroItemRequest> items;
    }
}
