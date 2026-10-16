package ru.dndmemory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dndmemory.domain.Character;
import ru.dndmemory.domain.Hero;
import ru.dndmemory.domain.Item;
import ru.dndmemory.domain.Location;
import ru.dndmemory.domain.ObjectType;
import ru.dndmemory.domain.Retelling;
import ru.dndmemory.repo.CharacterRepository;
import ru.dndmemory.repo.HeroRepository;
import ru.dndmemory.repo.ItemRepository;
import ru.dndmemory.repo.LocationRepository;
import ru.dndmemory.repo.RetellingRepository;
import ru.dndmemory.security.PermissionService;
import ru.dndmemory.web.dto.SearchDtos;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Поиск по кампании: по названию и описанию, у персонажа — ещё по расе и короткому
 * описанию, у пересказа — по заголовку и тексту. Тот же алгоритм повторяют клиенты
 * офлайн, поэтому результаты в сети и без неё совпадают.
 */
@Service
@RequiredArgsConstructor
public class SearchService {

    private static final int SNIPPET_RADIUS = 60;
    private static final int DEFAULT_LIMIT = 50;

    private final HeroRepository heroRepository;
    private final CharacterRepository characterRepository;
    private final LocationRepository locationRepository;
    private final ItemRepository itemRepository;
    private final RetellingRepository retellingRepository;
    private final PermissionService permissionService;
    private final ObjectIndexService objectIndexService;

    @Transactional(readOnly = true)
    public SearchDtos.SearchResponse search(UUID campaignId, UUID userId, String query,
                                            Set<ObjectType> kinds, int limit) {
        permissionService.requireMember(campaignId, userId);
        List<SearchDtos.SearchItemDto> items = new ArrayList<>();
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return SearchDtos.SearchResponse.builder().total(0).items(items).build();
        }

        if (wanted(kinds, ObjectType.HERO)) {
            for (Hero hero : heroRepository.findAllByCampaignIdAndDeletedFalseOrderByNameAsc(campaignId)) {
                add(items, ObjectType.HERO, hero.getId(), hero.getName(), hero.getRevision(), hero.getUpdatedAt(),
                        q, hero.getName(), hero.getDescription());
            }
        }
        if (wanted(kinds, ObjectType.CHARACTER)) {
            for (Character character : characterRepository.findAllByCampaignIdAndDeletedFalseOrderByNameAsc(campaignId)) {
                add(items, ObjectType.CHARACTER, character.getId(), character.getName(),
                        character.getRevision(), character.getUpdatedAt(), q,
                        character.getName(), character.getDescription(),
                        character.getRace(), character.getShortDescription());
            }
        }
        if (wanted(kinds, ObjectType.LOCATION)) {
            for (Location location : locationRepository.findAllByCampaignIdAndDeletedFalseOrderByNameAsc(campaignId)) {
                add(items, ObjectType.LOCATION, location.getId(), location.getName(),
                        location.getRevision(), location.getUpdatedAt(), q,
                        location.getName(), location.getDescription());
            }
        }
        if (wanted(kinds, ObjectType.ITEM)) {
            for (Item item : itemRepository.findAllByCampaignIdAndDeletedFalseOrderByNameAsc(campaignId)) {
                add(items, ObjectType.ITEM, item.getId(), item.getName(), item.getRevision(), item.getUpdatedAt(),
                        q, item.getName(), item.getDescription(), item.getOwnerName());
            }
        }
        if (wanted(kinds, ObjectType.RETELLING)) {
            for (Retelling retelling : retellingRepository
                    .findAllByCampaignIdAndDeletedFalseOrderByCreatedAtDesc(campaignId)) {
                add(items, ObjectType.RETELLING, retelling.getId(), retelling.getTitle(),
                        retelling.getRevision(), retelling.getUpdatedAt(), q,
                        retelling.getTitle(), retelling.getBody());
            }
        }

        items.sort(Comparator.comparing(SearchDtos.SearchItemDto::getUpdatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        long total = items.size();
        int pageSize = limit <= 0 ? DEFAULT_LIMIT : limit;
        if (items.size() > pageSize) {
            items = new ArrayList<>(items.subList(0, pageSize));
        }
        return SearchDtos.SearchResponse.builder().total(total).items(items).build();
    }

    /** Имена объектов, на которые ссылаются в тексте, но которых в кампании ещё нет. */
    @Transactional(readOnly = true)
    public Set<String> missingNames(UUID campaignId, String... texts) {
        return new HashSet<>(objectIndexService.missingNames(campaignId, texts));
    }

    private boolean wanted(Set<ObjectType> kinds, ObjectType kind) {
        return kinds == null || kinds.isEmpty() || kinds.contains(kind);
    }

    private void add(List<SearchDtos.SearchItemDto> items, ObjectType kind, UUID id, String title,
                     long revision, Instant updatedAt, String query, String... texts) {
        String haystack = join(texts);
        if (!haystack.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) {
            return;
        }
        items.add(SearchDtos.SearchItemDto.builder()
                .kind(kind)
                .id(id)
                .title(title)
                .snippet(snippet(haystack, query))
                .revision(revision)
                .updatedAt(updatedAt)
                .build());
    }

    private String join(String... texts) {
        StringBuilder sb = new StringBuilder();
        for (String text : texts) {
            if (text != null && !text.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(text);
            }
        }
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private String snippet(String text, String query) {
        if (text.isEmpty()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String q = query.toLowerCase(Locale.ROOT);
        int position = lower.indexOf(q);
        if (position < 0) {
            position = 0;
        }
        int from = Math.max(0, position - SNIPPET_RADIUS);
        int to = Math.min(text.length(), position + q.length() + SNIPPET_RADIUS);
        String fragment = text.substring(from, to);
        String highlighted = fragment.replaceAll("(?i)" + Pattern.quote(query), "<b>$0</b>");
        return (from > 0 ? "…" : "") + escapeExceptTags(highlighted) + (to < text.length() ? "…" : "");
    }

    /** Экранируем HTML, но оставляем подсветку <b>. */
    private String escapeExceptTags(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("&lt;b&gt;", "<b>").replace("&lt;/b&gt;", "</b>");
    }
}
