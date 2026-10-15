package ru.dndmemory.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.dndmemory.security.UserPrincipal;
import ru.dndmemory.service.SearchService;
import ru.dndmemory.web.dto.SearchDtos;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/api/campaigns/{campaignId}/search")
    public SearchDtos.SearchResponse search(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable UUID campaignId,
                                            @RequestParam(name = "q") String query,
                                            @RequestParam(required = false) List<String> types,
                                            @RequestParam(defaultValue = "50") int limit) {
        return searchService.search(campaignId, principal.getId(), query,
                types == null ? null : new HashSet<>(types), limit);
    }

    /** Имена, на которые ссылаются в тексте, но которых ещё нет — интерфейс предлагает их создать. */
    @GetMapping("/api/campaigns/{campaignId}/missing-names")
    public List<String> missingNames(@AuthenticationPrincipal UserPrincipal principal,
                                     @PathVariable UUID campaignId,
                                     @RequestParam String text) {
        return new java.util.ArrayList<>(searchService.missingNames(campaignId, text));
    }
}
