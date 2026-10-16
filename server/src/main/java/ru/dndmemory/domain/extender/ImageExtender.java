package ru.dndmemory.domain.extender;

import java.util.UUID;

/** Объект с картинкой: ссылка на загруженный файл. */
public interface ImageExtender {

    UUID getImageFileId();

    void setImageFileId(UUID imageFileId);
}
