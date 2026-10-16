package ru.dndmemory.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.dndmemory.domain.ObjectType;
import ru.dndmemory.security.UserPrincipal;
import ru.dndmemory.service.SearchService;
import ru.dndmemory.web.dto.SearchDtos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /** {@code kinds} — какие классы искать: HERO, CHARACTER, LOCATION, ITEM, RETELLING. */
    @GetMapping("/api/campaigns/{campaignId}/search")
    public SearchDtos.SearchResponse search(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable UUID campaignId,
                                            @RequestParam(name = "q") String query,
                                            @RequestParam(required = false) List<ObjectType> kinds,
                                            @RequestParam(defaultValue = "50") int limit) {
        return searchService.search(campaignId, principal.getId(), query,
                kinds == null || kinds.isEmpty() ? null : new HashSet<>(kinds), limit);
    }

    /** Имена, на которые ссылаются в тексте, но которых ещё нет — интерфейс предлагает их создать. */
    @GetMapping("/api/campaigns/{campaignId}/missing-names")
    public List<String> missingNames(@AuthenticationPrincipal UserPrincipal principal,
                                     @PathVariable UUID campaignId,
                                     @RequestParam String text) {
        return new ArrayList<>(searchService.missingNames(campaignId, text));
    }
}
