package ru.dndmemory.data.remote.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ru.dndmemory.data.model.EntityLink;

/** Объект кампании (docs/api.md, раздел 6). Используется и в телах запросов. */
public class EntityDto {

    public String id;
    public String campaignId;

    /** UUID создания — ключ идемпотентности для офлайн-созданных объектов. */
    public String clientId;

    /** HERO | NPC | LOCATION | ITEM */
    public String type;

    public String name;
    public String summary;
    public String description;

    public Map<String, String> attrs = new HashMap<>();
    public List<String> tags = new ArrayList<>();
    public List<EntityLink> links = new ArrayList<>();

    public String imageFileId;

    public String createdBy;
    public String updatedBy;
    public long revision;
    public String createdAt;
    public String updatedAt;

    public boolean deleted;
}
