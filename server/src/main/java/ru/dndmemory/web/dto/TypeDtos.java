package ru.dndmemory.web.dto;

import lombok.Builder;
import lombok.Data;

/** Элемент справочника типов: {@code code} — ключ, {@code name} — подпись. */
public final class TypeDtos {

    private TypeDtos() {
    }

    @Data
    @Builder
    public static class TypeDto {
        private String code;
        private String name;
    }
}
