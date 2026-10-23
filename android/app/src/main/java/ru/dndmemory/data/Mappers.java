package ru.dndmemory.data;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ru.dndmemory.data.local.entity.CampaignRoom;
import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.data.local.entity.MaterialRoom;
import ru.dndmemory.data.local.entity.MemberRoom;
import ru.dndmemory.data.local.entity.RetellingRoom;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import ru.dndmemory.data.model.Attachment;
import ru.dndmemory.data.model.EntityLink;
import ru.dndmemory.data.model.EntityType;
import ru.dndmemory.data.model.HeroItem;
import ru.dndmemory.data.remote.dto.CampaignDto;
import ru.dndmemory.data.remote.dto.CharacterDto;
import ru.dndmemory.data.remote.dto.EntityDto;
import ru.dndmemory.data.remote.dto.HeroDto;
import ru.dndmemory.data.remote.dto.ItemDto;
import ru.dndmemory.data.remote.dto.LocationDto;
import ru.dndmemory.data.remote.dto.MaterialDto;
import ru.dndmemory.data.remote.dto.MemberDto;
import ru.dndmemory.data.remote.dto.RetellingDto;

/**
 * Преобразование «сеть ↔ локальная база». Единственное место, где сопоставляются
 * DTO сервера и Room-сущности, поэтому его же использует {@code SyncEngine}
 * при применении журнала изменений.
 *
 * <p>У объектов кампании четыре класса живут в одной таблице с дискриминатором
 * {@code kind}, но в теле запроса и ответа всегда форма DTO своего класса
 * (docs/api.md, раздел 6): {@link #toDto(EntityRoom)} собирает нужный подкласс
 * по {@code kind}, а {@link #toRoom(EntityDto, EntityType, String)} читает поля
 * только своего класса.
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

    // --- Объекты кампании ----------------------------------------------------

    /** DTO нужного класса: сервер ждёт ровно форму своего класса. */
    public static EntityDto newDto(@Nullable EntityType type) {
        EntityType kind = type == null ? EntityType.CHARACTER : type;
        switch (kind) {
            case HERO:
                return new HeroDto();
            case LOCATION:
                return new LocationDto();
            case ITEM:
                return new ItemDto();
            case CHARACTER:
            default:
                return new CharacterDto();
        }
    }

    /** Класс DTO по значению {@code objectType} журнала изменений. */
    public static Class<? extends EntityDto> dtoClass(EntityType type) {
        EntityType kind = type == null ? EntityType.CHARACTER : type;
        switch (kind) {
            case HERO:
                return HeroDto.class;
            case LOCATION:
                return LocationDto.class;
            case ITEM:
                return ItemDto.class;
            case CHARACTER:
            default:
                return CharacterDto.class;
        }
    }

    public static EntityRoom toRoom(EntityDto dto, EntityType type, String fallbackCampaignId) {
        EntityRoom room = new EntityRoom();
        if (dto == null) {
            return room;
        }
        EntityType kind = type == null ? EntityType.CHARACTER : type;
        room.id = nz(dto.id);
        room.campaignId = dto.campaignId != null ? dto.campaignId : fallbackCampaignId;
        room.kind = kind.name();
        room.name = dto.name;
        room.gender = dto.gender;
        room.description = dto.description;
        room.imageFileId = dto.imageFileId;
        room.avatarFileId = dto.avatarFileId;
        room.filesJson = filesJson(dto.files);
        room.links = dto.links == null ? new ArrayList<EntityLink>() : new ArrayList<>(dto.links);
        room.createdBy = dto.createdBy;
        room.updatedBy = dto.updatedBy;
        room.revision = dto.revision;
        room.createdAt = dto.createdAt;
        room.updatedAt = dto.updatedAt;
        room.deleted = dto.deleted;
        room.dirty = false;
        room.conflictJson = null;

        if (kind == EntityType.HERO && dto instanceof HeroDto) {
            HeroDto hero = (HeroDto) dto;
            room.race = hero.race;
            room.klass = hero.klass;
            room.playerUserId = hero.playerUserId;
            room.playerName = hero.playerName;
            room.items = hero.items == null ? new ArrayList<HeroItem>() : new ArrayList<>(hero.items);
        } else if (kind == EntityType.CHARACTER && dto instanceof CharacterDto) {
            CharacterDto character = (CharacterDto) dto;
            room.race = character.race;
            room.klass = character.klass;
            room.shortDescription = character.shortDescription;
        } else if (kind == EntityType.LOCATION && dto instanceof LocationDto) {
            room.locationType = ((LocationDto) dto).locationType;
        } else if (kind == EntityType.ITEM && dto instanceof ItemDto) {
            ItemDto item = (ItemDto) dto;
            room.itemType = item.itemType;
            room.ownerName = item.ownerName;
            room.ownerHeroId = item.ownerHeroId;
            room.ownerHeroName = item.ownerHeroName;
            room.ownerCharacterId = item.ownerCharacterId;
            room.ownerCharacterName = item.ownerCharacterName;
        }
        return room;
    }

    /** Фото-материал: сервер → локальная база. */
    public static MaterialRoom toRoom(MaterialDto dto, String fallbackCampaignId) {
        MaterialRoom room = new MaterialRoom();
        if (dto == null) {
            return room;
        }
        room.id = nz(dto.id);
        room.campaignId = dto.campaignId != null ? dto.campaignId : fallbackCampaignId;
        room.name = dto.name;
        room.description = dto.description;
        room.imageFileId = dto.imageFileId;
        room.avatarFileId = dto.avatarFileId;
        room.filesJson = filesJson(dto.files);
        room.linksJson = linksJson(dto.links);
        room.revision = dto.revision;
        room.createdAt = dto.createdAt;
        room.updatedAt = dto.updatedAt;
        room.deleted = dto.deleted;
        room.dirty = false;
        room.conflictJson = null;
        return room;
    }

    /** Фото-материал: локальная база → тело правки для push. */
    public static MaterialDto toDto(MaterialRoom room) {
        MaterialDto dto = new MaterialDto();
        if (room == null) {
            return dto;
        }
        dto.id = room.id;
        dto.campaignId = room.campaignId;
        dto.name = room.name;
        dto.description = room.description;
        dto.imageFileId = room.imageFileId;
        dto.avatarFileId = room.avatarFileId;
        dto.files = files(room.filesJson);
        dto.links = links(room.linksJson);
        dto.revision = room.revision;
        dto.createdAt = room.createdAt;
        dto.updatedAt = room.updatedAt;
        dto.deleted = room.deleted;
        return dto;
    }

    private static final Gson FILES_GSON = new Gson();

    /** Ссылки из JSON-колонки; битый JSON не роняет карточку. */
    @NonNull
    public static java.util.List<EntityLink> links(@Nullable String json) {
        if (json == null || json.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        try {
            java.util.List<EntityLink> parsed = FILES_GSON.fromJson(json,
                    new TypeToken<java.util.List<EntityLink>>() { }.getType());
            return parsed == null ? new java.util.ArrayList<>() : parsed;
        } catch (RuntimeException e) {
            return new java.util.ArrayList<>();
        }
    }

    @Nullable
    public static String linksJson(@Nullable java.util.List<EntityLink> links) {
        if (links == null || links.isEmpty()) {
            return null;
        }
        return FILES_GSON.toJson(links);
    }

    /** Файлы объекта из JSON-колонки; битый JSON не роняет список. */
    @NonNull
    public static java.util.List<Attachment> files(String json) {
        if (json == null || json.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        try {
            java.util.List<Attachment> parsed = FILES_GSON.fromJson(json,
                    new TypeToken<java.util.List<Attachment>>() { }.getType());
            return parsed == null ? new java.util.ArrayList<>() : parsed;
        } catch (RuntimeException e) {
            return new java.util.ArrayList<>();
        }
    }

    /** Файлы в JSON для локальной колонки и для payload. */
    @Nullable
    public static String filesJson(@Nullable java.util.List<Attachment> files) {
        if (files == null || files.isEmpty()) {
            return null;
        }
        return FILES_GSON.toJson(files);
    }

    /** Тело правки: подкласс DTO по классу объекта, лишние поля не отправляются. */
    public static EntityDto toDto(EntityRoom room) {
        if (room == null) {
            return new CharacterDto();
        }
        EntityType kind = EntityType.fromOrCharacter(room.kind);
        EntityDto dto = newDto(kind);

        dto.id = room.id;
        dto.campaignId = room.campaignId;
        dto.name = room.name;
        dto.description = room.description;
        dto.gender = room.gender;
        dto.imageFileId = room.imageFileId;
        dto.avatarFileId = room.avatarFileId;
        dto.files = files(room.filesJson);
        dto.links = room.links == null ? new ArrayList<EntityLink>() : room.links;
        dto.createdBy = room.createdBy;
        dto.updatedBy = room.updatedBy;
        dto.revision = room.revision;
        dto.createdAt = room.createdAt;
        dto.updatedAt = room.updatedAt;
        dto.deleted = room.deleted;

        if (dto instanceof LocationDto) {
            ((LocationDto) dto).locationType = room.locationType;
        }
        if (dto instanceof HeroDto) {
            HeroDto hero = (HeroDto) dto;
            hero.race = room.race;
            hero.klass = room.klass;
            hero.playerUserId = room.playerUserId;
            hero.items = room.items == null ? new ArrayList<HeroItem>() : room.items;
        } else if (dto instanceof CharacterDto) {
            CharacterDto character = (CharacterDto) dto;
            character.race = room.race;
            character.klass = room.klass;
            character.shortDescription = room.shortDescription;
        } else if (dto instanceof ItemDto) {
            ItemDto item = (ItemDto) dto;
            item.itemType = room.itemType;
            item.ownerName = room.ownerName;
            item.ownerHeroId = room.ownerHeroId;
            item.ownerCharacterId = room.ownerCharacterId;
        }
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
        room.linkedHeroId = dto.linkedHeroId;
        room.linkedHeroName = dto.linkedHeroName;
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
        dto.linkedHeroId = room.linkedHeroId;
        dto.linkedHeroName = room.linkedHeroName;
        dto.status = room.status;
        dto.createdAt = room.createdAt;
        return dto;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
