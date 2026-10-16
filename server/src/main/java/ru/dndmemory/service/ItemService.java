package ru.dndmemory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dndmemory.domain.Item;
import ru.dndmemory.domain.ObjectType;
import ru.dndmemory.repo.CampaignRepository;
import ru.dndmemory.repo.GameEntityRepository;
import ru.dndmemory.repo.ItemRepository;
import ru.dndmemory.repo.MemberRepository;
import ru.dndmemory.security.PermissionService;
import ru.dndmemory.web.ApiException;
import ru.dndmemory.web.dto.ItemDtos;

import java.util.UUID;

/** Предмет: владелец «как написано в мире» и необязательная ссылка на героя или персонажа. */
@Service
public class ItemService extends AbstractObjectService<Item, ItemDtos.ItemDto> {

    private final ItemRepository itemRepository;

    public ItemService(ItemRepository itemRepository,
                       CampaignRepository campaignRepository,
                       MemberRepository memberRepository,
                       PermissionService permissionService,
                       ChangeRecorder changeRecorder,
                       ObjectIndexService objectIndexService,
                       ConflictRecorder conflictRecorder,
                       ObjectMapper objectMapper) {
        super(campaignRepository, memberRepository, permissionService, changeRecorder,
                objectIndexService, conflictRecorder, objectMapper);
        this.itemRepository = itemRepository;
    }

    @Override
    public ObjectType objectType() {
        return ObjectType.ITEM;
    }

    @Override
    protected GameEntityRepository<Item> repository() {
        return itemRepository;
    }

    @Override
    protected Item newEntity() {
        return new Item();
    }

    @Override
    protected String entityLabel() {
        return "Предмет";
    }

    @Transactional
    public ItemDtos.ItemDto create(UUID campaignId, UUID userId, ItemDtos.CreateItemRequest request) {
        return create(campaignId, userId, nonNull(request), request.getId());
    }

    @Transactional
    public ItemDtos.ItemDto update(UUID campaignId, UUID itemId, UUID userId,
                                   ItemDtos.UpdateItemRequest request, Long ifMatchRevision) {
        return update(campaignId, itemId, userId, nonNull(request), ifMatchRevision);
    }

    /** Ссылка максимум на одного владельца: либо герой, либо персонаж. */
    @Override
    protected void validate(Item item) {
        if (item.getOwnerHeroId() != null && item.getOwnerCharacterId() != null) {
            throw ApiException.validation("Укажите владельца — героя или персонажа, но не обоих сразу");
        }
    }

    @Override
    protected ItemDtos.ItemDto toDto(Item item, ObjectIndexService.ObjectContext context) {
        return ItemDtos.ItemDto.builder()
                .id(item.getId())
                .campaignId(item.getCampaignId())
                .name(item.getName())
                .description(item.getDescription())
                .imageFileId(item.getImageFileId())
                .links(objectIndexService.links(context, item.getDescription()))
                .createdBy(item.getCreatedBy())
                .createdByName(context.userName(item.getCreatedBy()))
                .updatedBy(item.getUpdatedBy())
                .updatedByName(context.userName(item.getUpdatedBy()))
                .revision(item.getRevision())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .deleted(item.isDeleted())
                .ownerName(item.getOwnerName())
                .ownerHeroId(item.getOwnerHeroId())
                .ownerHeroName(context.nameOf(item.getOwnerHeroId()))
                .ownerCharacterId(item.getOwnerCharacterId())
                .ownerCharacterName(context.nameOf(item.getOwnerCharacterId()))
                .build();
    }

    @Override
    protected void applyPayload(Item item, JsonNode payload) {
        if (payload == null) {
            return;
        }
        if (payload.hasNonNull("name")) {
            item.setName(payload.get("name").asText());
        }
        if (payload.has("description")) {
            item.setDescription(text(payload.get("description")));
        }
        if (payload.has("imageFileId")) {
            item.setImageFileId(uuidOrNull(payload.get("imageFileId")));
        }
        if (payload.has("ownerName")) {
            item.setOwnerName(text(payload.get("ownerName")));
        }
        if (payload.has("ownerHeroId")) {
            item.setOwnerHeroId(uuidOrNull(payload.get("ownerHeroId")));
        }
        if (payload.has("ownerCharacterId")) {
            item.setOwnerCharacterId(uuidOrNull(payload.get("ownerCharacterId")));
        }
    }
}
