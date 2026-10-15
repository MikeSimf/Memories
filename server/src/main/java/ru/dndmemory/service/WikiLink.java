package ru.dndmemory.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/** Разобранная ссылка вида [[Имя]] из текста описания. */
@Getter
@AllArgsConstructor
public class WikiLink {

    private final UUID targetId;
    private final String label;
    private final boolean resolved;
}
