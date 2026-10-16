package ru.dndmemory.domain.extender;

/**
 * Именованный объект: название и описание.
 *
 * <p>В описании поддерживаются ссылки {@code [[Имя]]}, {@code [[Имя|подпись]]} и
 * {@code [[#id|подпись]]} — их разрешает {@code WikiLinkService}.</p>
 */
public interface NamedExtender {

    String getName();

    void setName(String name);

    String getDescription();

    void setDescription(String description);

    /**
     * Нормализованный ключ сопоставления: два объекта с одинаковым ключом в одной
     * кампании считаются одним и тем же. Правило задаёт сам класс — чтобы изменить
     * его, достаточно одной строки в наследнике (например, у персонажа имя + раса).
     */
    String matchKey();

    /** Причина совпадения для журнала синхронизации, когда объект найден по ключу. */
    default String matchReason() {
        return "matched_by_name";
    }
}
