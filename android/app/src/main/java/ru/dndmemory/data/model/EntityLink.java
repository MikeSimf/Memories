package ru.dndmemory.data.model;

/**
 * Разобранная ссылка {@code [[Имя]]} внутри текста объекта.
 * Формат совпадает с полем {@code links} в ответах сервера (docs/api.md, раздел 6).
 */
public class EntityLink {

    /** id объекта-цели или {@code null}, если имя не разрешилось. */
    public String targetId;

    /** Подпись, которую показываем в тексте. */
    public String label;

    /** Разрешилась ли ссылка на существующий объект. */
    public boolean resolved;

    /**
     * Имя, как оно записано в тексте ({@code [[Имя]]} или {@code [[#uuid|подпись]]}).
     * Локальный парсер заполняет его сам, сервер может не присылать.
     */
    public String name;

    public EntityLink() {
    }

    public EntityLink(String targetId, String label, boolean resolved, String name) {
        this.targetId = targetId;
        this.label = label;
        this.resolved = resolved;
        this.name = name;
    }

    @Override
    public String toString() {
        return "EntityLink{targetId=" + targetId + ", label=" + label + ", resolved=" + resolved + '}';
    }
}
