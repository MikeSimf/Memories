package ru.dndmemory.data;

import java.util.ArrayList;
import java.util.HashMap;

import ru.dndmemory.data.local.entity.CampaignRoom;
import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.data.local.entity.MemberRoom;
import ru.dndmemory.data.local.entity.RetellingRoom;
import ru.dndmemory.data.model.EntityLink;
import ru.dndmemory.data.remote.dto.CampaignDto;
import ru.dndmemory.data.remote.dto.EntityDto;
import ru.dndmemory.data.remote.dto.MemberDto;
import ru.dndmemory.data.remote.dto.RetellingDto;

/**
 * Преобразование «сеть ↔ локальная база». Единственное место, где сопоставляются
 * DTO сервера и Room-сущности, поэтому его же использует {@code SyncEngine}
 * при применении журнала изменений.
 */
public final class Mappers {

    private Mappers() {
    }

    // --- Кампании -----------------------------------------------------------

    public static CampaignRoom toRoom(CampaignDto dto) {
        CampaignRoom room = new CampaignRoom();
        if (dto == null) {
            return room;
        }
        room.id = nz(dto.id);
        room.title = dto.title;
        room.description = dto.description;
        room.myRole = dto.myRole;
        room.myPermissions = dto.myPermissions == null ? new ArrayList<>() : dto.myPermissions;
        room.memberCount = dto.memberCount;
        room.entityCount = dto.entityCount;
        room.retellingCount = dto.retellingCount;
        room.latestRevision = dto.latestRevision;
        room.updatedAt = dto.updatedAt;
        return room;
    }

    public static CampaignDto toDto(CampaignRoom room) {
        CampaignDto dto = new CampaignDto();
        if (room == null) {
            return dto;
        }
        dto.id = room.id;
        dto.title = room.title;
        dto.description = room.description;
        dto.myRole = room.myRole;
        dto.myPermissions = room.myPermissions;
        dto.memberCount = room.memberCount;
        dto.entityCount = room.entityCount;
        dto.retellingCount = room.retellingCount;
        dto.latestRevision = room.latestRevision;
        dto.updatedAt = room.updatedAt;
        return dto;
    }

    // --- Объекты ------------------------------------------------------------

    public static EntityRoom toRoom(EntityDto dto, String fallbackCampaignId) {
        EntityRoom room = new EntityRoom();
        if (dto == null) {
            return room;
        }
        room.id = nz(dto.id);
        room.campaignId = dto.campaignId != null ? dto.campaignId : fallbackCampaignId;
        room.clientId = dto.clientId;
        room.type = dto.type;
        room.name = dto.name;
        room.summary = dto.summary;
        room.description = dto.description;
        room.attrs = dto.attrs == null ? new HashMap<>() : new HashMap<>(dto.attrs);
        room.tags = dto.tags == null ? new ArrayList<>() : new ArrayList<>(dto.tags);
        room.links = dto.links == null ? new ArrayList<EntityLink>() : new ArrayList<>(dto.links);
        room.imageFileId = dto.imageFileId;
        room.createdBy = dto.createdBy;
        room.updatedBy = dto.updatedBy;
        room.revision = dto.revision;
        room.createdAt = dto.createdAt;
        room.updatedAt = dto.updatedAt;
        room.deleted = dto.deleted;
        room.dirty = false;
        room.conflictJson = null;
        return room;
    }

    public static EntityDto toDto(EntityRoom room) {
        EntityDto dto = new EntityDto();
        if (room == null) {
            return dto;
        }
        dto.id = room.id;
        dto.campaignId = room.campaignId;
        dto.clientId = room.clientId == null ? room.id : room.clientId;
        dto.type = room.type;
        dto.name = room.name;
        dto.summary = room.summary;
        dto.description = room.description;
        dto.attrs = room.attrs == null ? new HashMap<>() : room.attrs;
        dto.tags = room.tags == null ? new ArrayList<>() : room.tags;
        dto.links = room.links == null ? new ArrayList<EntityLink>() : room.links;
        dto.imageFileId = room.imageFileId;
        dto.createdBy = room.createdBy;
        dto.updatedBy = room.updatedBy;
        dto.revision = room.revision;
        dto.createdAt = room.createdAt;
        dto.updatedAt = room.updatedAt;
        dto.deleted = room.deleted;
        return dto;
    }

    // --- Пересказы ----------------------------------------------------------

    public static RetellingRoom toRoom(RetellingDto dto, String fallbackCampaignId) {
        RetellingRoom room = new RetellingRoom();
        if (dto == null) {
            return room;
        }
        room.id = nz(dto.id);
        room.campaignId = dto.campaignId != null ? dto.campaignId : fallbackCampaignId;
        room.clientId = dto.clientId;
        room.title = dto.title;
        room.body = dto.body;
        room.authorEntityId = dto.authorEntityId;
        room.authorEntityName = dto.authorEntityName;
        room.sessionDate = dto.sessionDate;
        room.status = dto.status == null ? "PUBLISHED" : dto.status;
        room.links = dto.links == null ? new ArrayList<EntityLink>() : new ArrayList<>(dto.links);
        room.revision = dto.revision;
        room.createdBy = dto.createdBy;
        room.updatedBy = dto.updatedBy;
        room.createdAt = dto.createdAt;
        room.updatedAt = dto.updatedAt;
        room.deleted = dto.deleted;
        room.dirty = false;
        room.conflictJson = null;
        return room;
    }

    public static RetellingDto toDto(RetellingRoom room) {
        RetellingDto dto = new RetellingDto();
        if (room == null) {
            return dto;
        }
        dto.id = room.id;
        dto.campaignId = room.campaignId;
        dto.clientId = room.clientId == null ? room.id : room.clientId;
        dto.title = room.title;
        dto.body = room.body;
        dto.authorEntityId = room.authorEntityId;
        dto.authorEntityName = room.authorEntityName;
        dto.sessionDate = room.sessionDate;
        dto.status = room.status == null ? "PUBLISHED" : room.status;
        dto.links = room.links == null ? new ArrayList<EntityLink>() : room.links;
        dto.revision = room.revision;
        dto.createdBy = room.createdBy;
        dto.updatedBy = room.updatedBy;
        dto.createdAt = room.createdAt;
        dto.updatedAt = room.updatedAt;
        dto.deleted = room.deleted;
        return dto;
    }

    // --- Участники ----------------------------------------------------------

    public static MemberRoom toRoom(MemberDto dto, String campaignId) {
        MemberRoom room = new MemberRoom();
        if (dto == null) {
            return room;
        }
        room.id = nz(dto.id);
        room.campaignId = campaignId;
        room.userId = dto.userId;
        room.displayName = dto.displayName;
        room.email = dto.email;
        room.role = dto.role;
        room.linkedEntityId = dto.linkedEntityId;
        room.linkedEntityName = dto.linkedEntityName;
        room.status = dto.status;
        room.createdAt = dto.createdAt;
        return room;
    }

    public static MemberDto toDto(MemberRoom room) {
        MemberDto dto = new MemberDto();
        if (room == null) {
            return dto;
        }
        dto.id = room.id;
        dto.userId = room.userId;
        dto.displayName = room.displayName;
        dto.email = room.email;
        dto.role = room.role;
        dto.linkedEntityId = room.linkedEntityId;
        dto.linkedEntityName = room.linkedEntityName;
        dto.status = room.status;
        dto.createdAt = room.createdAt;
        return dto;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
