package ru.dndmemory.web.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Общая форма ответа для всех четырёх классов справочника: одинаковые поля плюс
 * поля своего класса в наследниках ({@link HeroDtos.HeroDto} и т.д.).
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ObjectDto {

    private UUID id;
    private UUID campaignId;
    private String name;
    private String description;
    private UUID imageFileId;
    /** Файлы объекта: фото галереи, карты, документы. */
    private List<AttachmentDto> files;
    private List<LinkDto> links;
    private UUID createdBy;
    private String createdByName;
    private UUID updatedBy;
    private String updatedByName;
    private long revision;
    private Instant createdAt;
    private Instant updatedAt;
    private boolean deleted;
}
