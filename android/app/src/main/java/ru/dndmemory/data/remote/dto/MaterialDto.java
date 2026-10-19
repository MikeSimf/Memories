package ru.dndmemory.data.remote.dto;

import java.util.List;

import ru.dndmemory.data.model.EntityLink;

/**
 * Фото-материал кампании (docs/api.md, раздел 6): картинка плюс название
 * и описание. Тело push-изменения совпадает с этим DTO.
 */
public class MaterialDto {

    public String id;
    public String campaignId;
    public String name;
    public String description;
    public String imageFileId;
    public List<EntityLink> links;
    public String createdBy;
    public String updatedBy;
    public long revision;
    public String createdAt;
    public String updatedAt;
    public boolean deleted;
}
