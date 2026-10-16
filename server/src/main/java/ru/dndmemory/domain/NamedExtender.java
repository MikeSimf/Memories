package ru.dndmemory.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import java.util.Locale;

/**
 * Именованный объект: название и описание. Здесь же объявлен контракт ключа
 * сопоставления — по нему синхронизация узнаёт, что присланный клиентом объект уже
 * есть в кампании, и не создаёт дубль (docs/redesign-v2.md, раздел 2).
 */
@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
public abstract class NamedExtender extends GameEntityExtender {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    /**
     * Нормализованный ключ сопоставления: два объекта с одинаковым ключом в одной
     * кампании считаются одним и тем же объектом. Правило задаёт сам класс — чтобы
     * изменить его, достаточно одной строки в наследнике.
     */
    public abstract String matchKey();

    /** Причина конфликта для журнала, когда объект найден по ключу. */
    public String matchReason() {
        return "matched_by_name";
    }

    /** Часть ключа: нижний регистр, схлопнутые пробелы, обрезанные края. */
    protected static String normalizePart(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
