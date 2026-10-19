package ru.dndmemory.data.model;

/**
 * Файл объекта или материала: фото, карта, документ.
 * Лежит в списке {@code files} и уезжает на сервер вместе с объектом.
 */
public class Attachment {

    public String id;
    public String name;
    public String contentType;
    public long size;

    public Attachment() {
    }

    public Attachment(String id, String name, String contentType, long size) {
        this.id = id;
        this.name = name;
        this.contentType = contentType;
        this.size = size;
    }

    /** Картинку показываем в просмотрщике, документ предлагаем скачать. */
    public boolean isImage() {
        return contentType != null && contentType.startsWith("image/");
    }
}
