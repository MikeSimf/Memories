package ru.dndmemory.data.remote.dto;

/** Элемент GET /api/campaigns/{id}/entities/{entityId}/backlinks. */
public class BacklinkDto {

    public String id;
    /** ENTITY | RETELLING */
    public String kind;
    /** Тип объекта для ENTITY, иначе null. */
    public String type;
    public String title;
    public String snippet;
    public String updatedAt;
}
