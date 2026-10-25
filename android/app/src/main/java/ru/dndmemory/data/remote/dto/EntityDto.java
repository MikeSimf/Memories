package ru.dndmemory.data.remote.dto;

import java.util.ArrayList;
import java.util.List;

import ru.dndmemory.data.model.EntityLink;

/**
 * Общие поля объекта кампании (docs/api.md, раздел 6). Базовый класс для
 * {@link HeroDto}, {@link CharacterDto}, {@link LocationDto} и {@link ItemDto}:
 * тело запроса и ответа всегда в форме DTO своего класса, лишних полей нет.
 */
public class EntityDto {

    public String id;
    public String campaignId;

    public String name;
    public String description;

    public String imageFileId;
    public String avatarFileId;
    public String gender;
    public java.util.List<ru.dndmemory.data.model.Attachment> files;

    public List<EntityLink> links = new ArrayList<>();

    public String createdBy;
    public String createdByName;
    public String updatedBy;
    public String updatedByName;
    public long revision;
    public String createdAt;
    public String updatedAt;

    public boolean deleted;
}
