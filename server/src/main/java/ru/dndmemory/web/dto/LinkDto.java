package ru.dndmemory.web.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/** Разобранная ссылка {@code [[Имя]]} из описания объекта или пересказа. */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class LinkDto {

    private UUID targetId;
    private String label;
    private boolean resolved;
}
