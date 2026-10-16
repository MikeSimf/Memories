package ru.dndmemory.web.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.UUID;

/** Локация: своих полей нет. */
public final class LocationDtos {

    private LocationDtos() {
    }

    /** Своих полей нет, конструкторы генерирует только @SuperBuilder. */
    @Getter
    @Setter
    @SuperBuilder
    public static class LocationDto extends ObjectDto {
    }

    @Getter
    @Setter
    public static class CreateLocationRequest {
        private UUID id;

        @NotBlank(message = "Укажите название локации")
        @Size(max = 200, message = "Не длиннее 200 символов")
        private String name;

        private String description;

        private UUID imageFileId;
    }

    @Getter
    @Setter
    public static class UpdateLocationRequest {
        @Size(max = 200, message = "Не длиннее 200 символов")
        private String name;

        private String description;

        private UUID imageFileId;
    }
}
