package ru.dndmemory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dndmemory.domain.Character;
import ru.dndmemory.domain.Hero;
import ru.dndmemory.domain.Item;
import ru.dndmemory.domain.Location;
import ru.dndmemory.domain.User;
import ru.dndmemory.repo.CharacterRepository;
import ru.dndmemory.repo.HeroRepository;
import ru.dndmemory.repo.ItemRepository;
import ru.dndmemory.repo.LocationRepository;
import ru.dndmemory.repo.UserRepository;
import ru.dndmemory.web.dto.LinkDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Сводный индекс четырёх классов кампании: разрешение ссылок {@code [[Имя]]},
 * имена авторов правок и сопоставление предметов героя с предметами кампании.
 * Один запрос к каждой таблице на контекст — данные кампании небольшие.
 */
@Service
@RequiredArgsConstructor
public class ObjectIndexService {

    private final HeroRepository heroRepository;
    private final CharacterRepository characterRepository;
    private final LocationRepository locationRepository;
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final WikiLinkService wikiLinkService;
    private final ru.dndmemory.repo.MemberRepository memberRepository;

    /** Снимок индексов кампании на время одного запроса. */
    public static class ObjectContext {
        private final Map<String, UUID> nameIndex;
        private final Map<UUID, String> namesById;
        private final Map<UUID, String> userNames;
        private final Map<String, UUID> itemNames;
        private final boolean gmNoteVisible;

        ObjectContext(Map<String, UUID> nameIndex, Map<UUID, String> namesById,
                      Map<UUID, String> userNames, Map<String, UUID> itemNames,
                      boolean gmNoteVisible) {
            this.gmNoteVisible = gmNoteVisible;
            this.nameIndex = nameIndex;
            this.namesById = namesById;
            this.userNames = userNames;
            this.itemNames = itemNames;
        }

        public boolean gmNoteVisible() {
            return gmNoteVisible;
        }

        public Map<String, UUID> nameIndex() {
            return nameIndex;
        }

        public String nameOf(UUID id) {
            return id == null ? null : namesById.get(id);
        }

        public String userName(UUID id) {
            return id == null ? null : userNames.get(id);
        }

        public UUID itemIdByName(String name) {
            return name == null ? null : itemNames.get(normalize(name));
        }
    }

    @Transactional(readOnly = true)
    public ObjectContext context(UUID campaignId) {
        List<Hero> heroes = heroRepository.findAllByCampaignIdAndDeletedFalseOrderByNameAsc(campaignId);
        List<Character> characters = characterRepository.findAllByCampaignIdAndDeletedFalseOrderByNameAsc(campaignId);
        List<Location> locations = locationRepository.findAllByCampaignIdAndDeletedFalseOrderByNameAsc(campaignId);
        List<Item> items = itemRepository.findAllByCampaignIdAndDeletedFalseOrderByNameAsc(campaignId);

        List<Named> all = new ArrayList<>();
        heroes.forEach(h -> all.add(new Named(h.getName(), h.getId(), h.getCreatedBy(), h.getUpdatedBy())));
        characters.forEach(c -> all.add(new Named(c.getName(), c.getId(), c.getCreatedBy(), c.getUpdatedBy())));
        locations.forEach(l -> all.add(new Named(l.getName(), l.getId(), l.getCreatedBy(), l.getUpdatedBy())));
        items.forEach(i -> all.add(new Named(i.getName(), i.getId(), i.getCreatedBy(), i.getUpdatedBy())));
        // Первый по алфавиту выигрывает при неоднозначности — правило docs/api.md
        all.sort(Comparator.comparing(named -> normalize(named.name)));

        Map<String, UUID> nameIndex = new LinkedHashMap<>();
        Map<UUID, String> namesById = new HashMap<>();
        Set<UUID> userIds = new HashSet<>();
        for (Named named : all) {
            nameIndex.putIfAbsent(normalize(named.name), named.id);
            namesById.put(named.id, named.name);
            if (named.createdBy != null) {
                userIds.add(named.createdBy);
            }
            if (named.updatedBy != null) {
                userIds.add(named.updatedBy);
            }
        }
        // Игроки, отыгрывающие героев: интерфейсу нужно их имя рядом с playerUserId
        for (Hero hero : heroes) {
            if (hero.getPlayerUserId() != null) {
                userIds.add(hero.getPlayerUserId());
            }
        }

        Map<String, UUID> itemNames = new LinkedHashMap<>();
        for (Item item : items) {
            itemNames.putIfAbsent(normalize(item.getName()), item.getId());
        }

        Map<UUID, String> userNames = new HashMap<>();
        // system не участник кампаний, но его имя нужно как автор изменений.
        userRepository.findByLogin("system").ifPresent(
                user -> userNames.put(user.getId(), user.getDisplayName()));
        if (!userIds.isEmpty()) {
            for (User user : userRepository.findAllById(userIds)) {
                userNames.put(user.getId(), user.getDisplayName());
            }
        }
        boolean gmVisible = false;
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof ru.dndmemory.security.UserPrincipal) {
            UUID viewer = ((ru.dndmemory.security.UserPrincipal) auth.getPrincipal()).getId();
            boolean superuser = userRepository.findById(viewer).map(u -> u.isSuperuser()).orElse(false);
            gmVisible = !superuser && memberRepository.findByCampaignIdAndUserId(campaignId, viewer)
                    .map(m -> m.getRole() == ru.dndmemory.domain.MemberRole.EDITOR_MASTER
                            || (m.getRole() == ru.dndmemory.domain.MemberRole.OWNER
                                    && memberRepository.countByCampaignIdAndRole(campaignId,
                                            ru.dndmemory.domain.MemberRole.EDITOR_MASTER) == 0))
                    .orElse(false);
        }
        return new ObjectContext(nameIndex, namesById, userNames, itemNames, gmVisible);
    }

    /** Разбирает ссылки {@code [[…]]} в текстах по именам всех четырёх классов. */
    public List<LinkDto> links(ObjectContext context, String... texts) {
        List<LinkDto> links = new ArrayList<>();
        for (WikiLink link : wikiLinkService.extract(context.nameIndex(), texts)) {
            links.add(LinkDto.builder()
                    .targetId(link.getTargetId())
                    .label(link.getLabel())
                    .resolved(link.isResolved())
                    .build());
        }
        return links;
    }

    /** Имена, на которые ссылаются в тексте, но которых в кампании ещё нет. */
    @Transactional(readOnly = true)
    public Set<String> missingNames(UUID campaignId, String... texts) {
        return wikiLinkService.unresolvedNames(context(campaignId).nameIndex(), texts);
    }

    /** Имя объекта любого класса по идентификатору. */
    @Transactional(readOnly = true)
    public String nameOf(UUID campaignId, UUID objectId) {
        return objectId == null ? null : context(campaignId).nameOf(objectId);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static final class Named {
        private final String name;
        private final UUID id;
        private final UUID createdBy;
        private final UUID updatedBy;

        private Named(String name, UUID id, UUID createdBy, UUID updatedBy) {
            this.name = name;
            this.id = id;
            this.createdBy = createdBy;
            this.updatedBy = updatedBy;
        }
    }
}
