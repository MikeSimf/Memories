package ru.dndmemory.domain.extender;

import java.util.UUID;

/** Объект с картинкой: ссылка на загруженный файл. */
public interface ImageExtender {

    /** Файлы объекта в JSON: фото галереи, карты, документы. */
    String getFiles();

    void setFiles(String files);

    UUID getImageFileId();

    void setImageFileId(UUID imageFileId);
}
