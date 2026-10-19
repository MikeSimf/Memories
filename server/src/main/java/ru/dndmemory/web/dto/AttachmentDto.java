package ru.dndmemory.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Файл объекта или материала: фото, карта, документ.
 * {@code contentType} отличает картинку от документа, {@code name} — подпись.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentDto {
    private UUID id;
    private String name;
    private String contentType;
    private Long size;
}
