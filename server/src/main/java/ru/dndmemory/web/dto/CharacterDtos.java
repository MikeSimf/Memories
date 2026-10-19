package ru.dndmemory.web.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.UUID;

/** Персонаж (НПС): раса и короткое описание — по ним тоже идёт поиск. */
public final class CharacterDtos {

    private CharacterDtos() {
    }

    @Getter
    @Setter
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CharacterDto extends ObjectDto {
        private String race;
        private String klass;
        private String shortDescription;
    }

    @Getter
    @Setter
    public static class CreateCharacterRequest {
        private UUID id;

        @NotBlank(message = "Укажите имя персонажа")
        @Size(max = 200, message = "Не длиннее 200 символов")
        private String name;

        private String description;

        private UUID imageFileId;

        /** Файлы: фото и документы. Форма совпадает с ответом. */
        private java.util.List<ru.dndmemory.web.dto.AttachmentDto> files;

        @Size(max = 120, message = "Не длиннее 120 символов")
        private String race;
        private String klass;

        private String shortDescription;
    }

    @Getter
    @Setter
    public static class UpdateCharacterRequest {
        @Size(max = 200, message = "Не длиннее 200 символов")
        private String name;

        private String description;

        private UUID imageFileId;

        /** Файлы: фото и документы. Форма совпадает с ответом. */
        private java.util.List<ru.dndmemory.web.dto.AttachmentDto> files;

        @Size(max = 120, message = "Не длиннее 120 символов")
        private String race;
        private String klass;

        private String shortDescription;
    }
}
