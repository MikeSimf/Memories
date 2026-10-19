package ru.dndmemory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dndmemory.domain.Hero;
import ru.dndmemory.domain.HeroItem;
import ru.dndmemory.domain.Member;
import ru.dndmemory.domain.ObjectType;
import ru.dndmemory.repo.CampaignRepository;
import ru.dndmemory.repo.GameEntityRepository;
import ru.dndmemory.repo.HeroItemRepository;
import ru.dndmemory.repo.HeroRepository;
import ru.dndmemory.repo.MemberRepository;
import ru.dndmemory.security.PermissionService;
import ru.dndmemory.security.Permissions;
import ru.dndmemory.web.dto.HeroDtos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Герой партии. Единственный класс, который правит только владелец кампании:
 * игроки и мастера ведут персонажей, локации и предметы, а героев партии — владелец
 * (docs/redesign-v2.md, раздел 3).
 */
@Service
public class HeroService extends AbstractObjectService<Hero, HeroDtos.HeroDto> {

    private final HeroRepository heroRepository;
    private final HeroItemRepository heroItemRepository;

    public HeroService(HeroRepository heroRepository,
                       HeroItemRepository heroItemRepository,
                       CampaignRepository campaignRepository,
                       MemberRepository memberRepository,
                       PermissionService permissionService,
                       ChangeRecorder changeRecorder,
                       ObjectIndexService objectIndexService,
                       ConflictRecorder conflictRecorder,
                       ObjectMapper objectMapper) {
        super(campaignRepository, memberRepository, permissionService, changeRecorder,
                objectIndexService, conflictRecorder, objectMapper);
        this.heroRepository = heroRepository;
        this.heroItemRepository = heroItemRepository;
    }

    @Override
    public ObjectType objectType() {
        return ObjectType.HERO;
    }

    @Override
    protected GameEntityRepository<Hero> repository() {
        return heroRepository;
    }

    @Override
    protected Hero newEntity() {
        return new Hero();
    }

    @Override
    protected String entityLabel() {
        return "Герой";
    }

    @Override
    protected void requireCreate(Member member) {
        permissionService.require(member, Permissions.HERO_WRITE);
    }

    @Override
    protected void requireWrite(Member member, Hero entity) {
        permissionService.require(member, Permissions.HERO_WRITE);
    }

    @Transactional
    public HeroDtos.HeroDto create(UUID campaignId, UUID userId, HeroDtos.CreateHeroRequest request) {
        return create(campaignId, userId, nonNull(request), request.getId());
    }

    @Transactional
    public HeroDtos.HeroDto update(UUID campaignId, UUID heroId, UUID userId,
                                   HeroDtos.UpdateHeroRequest request, Long ifMatchRevision) {
        return update(campaignId, heroId, userId, nonNull(request), ifMatchRevision);
    }

    @Override
    protected HeroDtos.HeroDto toDto(Hero hero, ObjectIndexService.ObjectContext context) {
        List<HeroDtos.HeroItemDto> items = new ArrayList<>();
        for (HeroItem item : heroItemRepository.findAllByHeroIdOrderByPositionAsc(hero.getId())) {
            items.add(HeroDtos.HeroItemDto.builder()
                    .id(item.getId())
                    .name(item.getName())
                    .itemId(item.getItemId())
                    .quantity(item.getQuantity())
                    .notes(item.getNotes())
                    .build());
        }
        return HeroDtos.HeroDto.builder()
                .id(hero.getId())
                .campaignId(hero.getCampaignId())
                .name(hero.getName())
                .description(hero.getDescription())
                .files(filesOf(hero))
                .imageFileId(hero.getImageFileId())
                .avatarFileId(hero.getAvatarFileId())
                .links(objectIndexService.links(context, hero.getDescription()))
                .createdBy(hero.getCreatedBy())
                .createdByName(context.userName(hero.getCreatedBy()))
                .updatedBy(hero.getUpdatedBy())
                .updatedByName(context.userName(hero.getUpdatedBy()))
                .revision(hero.getRevision())
                .createdAt(hero.getCreatedAt())
                .updatedAt(hero.getUpdatedAt())
                .deleted(hero.isDeleted())
                .race(hero.getRace())
                .klass(hero.getKlass())
                .playerUserId(hero.getPlayerUserId())
                .playerName(context.userName(hero.getPlayerUserId()))
                .items(items)
                .build();
    }

    @Override
    protected void applyPayload(Hero hero, JsonNode payload) {
        applyFiles(hero, payload);
        if (payload == null) {
            return;
        }
        if (payload.hasNonNull("name")) {
            hero.setName(payload.get("name").asText());
        }
        if (payload.has("description")) {
            hero.setDescription(text(payload.get("description")));
        }
        if (payload.has("imageFileId")) {
            hero.setImageFileId(uuidOrNull(payload.get("imageFileId")));
        }
        if (payload.has("avatarFileId")) {
            hero.setAvatarFileId(uuidOrNull(payload.get("avatarFileId")));
        }
        if (payload.has("race")) {
            hero.setRace(text(payload.get("race")));
        }
        if (payload.has("klass")) {
            hero.setKlass(text(payload.get("klass")));
        }
        if (payload.has("playerUserId")) {
            hero.setPlayerUserId(uuidOrNull(payload.get("playerUserId")));
        }
    }

    @Override
    protected void afterApply(Hero hero, JsonNode payload) {
        if (payload == null || !payload.has("items")) {
            return;
        }
        heroItemRepository.deleteAllByHeroId(hero.getId());
        JsonNode items = payload.get("items");
        if (items == null || !items.isArray()) {
            return;
        }
        ObjectIndexService.ObjectContext context = objectIndexService.context(hero.getCampaignId());
        int position = 0;
        for (JsonNode node : items) {
            if (node == null || node.isNull() || !node.hasNonNull("name")) {
                continue;
            }
            String name = node.get("name").asText().trim();
            if (name.isEmpty()) {
                continue;
            }
            HeroItem item = new HeroItem();
            item.setId(UUID.randomUUID());
            item.setHeroId(hero.getId());
            item.setName(name);
            // itemId задан клиентом либо находится по имени среди предметов кампании
            UUID itemId = uuidOrNull(node.get("itemId"));
            item.setItemId(itemId != null ? itemId : context.itemIdByName(name));
            if (node.hasNonNull("quantity")) {
                item.setQuantity(node.get("quantity").asInt());
            }
            if (node.has("notes")) {
                item.setNotes(text(node.get("notes")));
            }
            item.setPosition(position++);
            heroItemRepository.save(item);
        }
    }
}
