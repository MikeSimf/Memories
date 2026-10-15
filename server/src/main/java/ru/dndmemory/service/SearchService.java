package ru.dndmemory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dndmemory.domain.EntityType;
import ru.dndmemory.domain.GameEntity;
import ru.dndmemory.domain.Retelling;
import ru.dndmemory.repo.GameEntityRepository;
import ru.dndmemory.repo.RetellingRepository;
import ru.dndmemory.security.PermissionService;
import ru.dndmemory.web.dto.SearchDtos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Поиск по кампании. Тот же алгоритм повторяют клиенты офлайн (подстрока без учёта регистра,
 * подсветка совпадения), поэтому результаты в сети и без неё совпадают.
 */
@Service
@RequiredArgsConstructor
public class SearchService {

    private static final int SNIPPET_RADIUS = 60;

    private final GameEntityRepository entityRepository;
    private final RetellingRepository retellingRepository;
    private final EntityService entityService;
    private final PermissionService permissionService;

    @Transactional(readOnly = true)
    public SearchDtos.SearchResponse search(UUID campaignId, UUID userId, String query, Set<String> types, int limit) {
        permissionService.requireMember(campaignId, userId);
        List<SearchDtos.SearchItemDto> items = new ArrayList<>();
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return SearchDtos.SearchResponse.builder().total(0).items(items).build();
        }

        boolean includeEntities = types == null || types.isEmpty() || types.stream()
                .anyMatch(t -> isEntityType(t));
        boolean includeRetellings = types == null || types.isEmpty()
                || types.contains("RETELLING") || types.contains("RETELLINGS");

        if (includeEntities) {
            for (GameEntity entity : entityRepository.findAllByCampaignIdAndDeletedFalseOrderByNameAsc(campaignId)) {
                if (types != null && !types.isEmpty() && !types.contains(entity.getType().name())) {
                    continue;
                }
                if (!entityService.matches(entity, q)) {
                    continue;
                }
                items.add(SearchDtos.SearchItemDto.builder()
                        .kind("ENTITY")
                        .id(entity.getId())
                        .type(entity.getType())
                        .title(entity.getName())
                        .snippet(snippet(entity, q))
                        .revision(entity.getRevision())
                        .updatedAt(entity.getUpdatedAt())
                        .build());
            }
        }
        if (includeRetellings) {
            for (Retelling retelling : retellingRepository
                    .findAllByCampaignIdAndDeletedFalseOrderByCreatedAtDesc(campaignId)) {
                String haystack = (retelling.getTitle() == null ? "" : retelling.getTitle()) + " "
                        + (retelling.getBody() == null ? "" : retelling.getBody());
                if (!haystack.toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                items.add(SearchDtos.SearchItemDto.builder()
                        .kind("RETELLING")
                        .id(retelling.getId())
                        .type(null)
                        .title(retelling.getTitle())
                        .snippet(snippet(retelling.getTitle(), retelling.getBody(), q))
                        .revision(retelling.getRevision())
                        .updatedAt(retelling.getUpdatedAt())
                        .build());
            }
        }
        items.sort(Comparator.comparing(SearchDtos.SearchItemDto::getUpdatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        long total = items.size();
        if (items.size() > limit) {
            items = new ArrayList<>(items.subList(0, limit));
        }
        return SearchDtos.SearchResponse.builder().total(total).items(items).build();
    }

    /** Имена объектов, на которые ссылаются в тексте, но которых в кампании ещё нет. */
    @Transactional(readOnly = true)
    public Set<String> missingNames(UUID campaignId, String... texts) {
        return new HashSet<>(new WikiLinkService()
                .unresolvedNames(entityService.nameIndex(campaignId), texts));
    }

    private boolean isEntityType(String value) {
        for (EntityType type : EntityType.values()) {
            if (type.name().equals(value)) {
                return true;
            }
        }
        return false;
    }

    private String snippet(GameEntity entity, String query) {
        return snippet(entity.getSummary() != null ? entity.getSummary() : entity.getName(),
                entity.getDescription(), query);
    }

    private String snippet(String title, String body, String query) {
        String text = (title == null ? "" : title) + (body == null ? "" : " " + body);
        text = text.trim().replaceAll("\\s+", " ");
        String lower = text.toLowerCase(Locale.ROOT);
        String q = query.toLowerCase(Locale.ROOT);
        int position = lower.indexOf(q);
        if (position < 0) {
            position = 0;
        }
        int from = Math.max(0, position - SNIPPET_RADIUS);
        int to = Math.min(text.length(), position + q.length() + SNIPPET_RADIUS);
        String fragment = text.substring(from, to);
        String highlighted = fragment.replaceAll("(?i)" + java.util.regex.Pattern.quote(query),
                "<b>$0</b>");
        return (from > 0 ? "…" : "") + escapeExceptTags(highlighted) + (to < text.length() ? "…" : "");
    }

    /** Экранируем HTML, но оставляем подсветку <b>. */
    private String escapeExceptTags(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("&lt;b&gt;", "<b>").replace("&lt;/b&gt;", "</b>");
    }
}
