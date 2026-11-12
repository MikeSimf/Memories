package ru.dndmemory.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.dndmemory.domain.CardTemplate;
import ru.dndmemory.security.PermissionService;
import ru.dndmemory.security.UserPrincipal;
import ru.dndmemory.service.CardTemplateService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Шаблоны карточек, доступные участникам кампании. */
@RestController
@RequiredArgsConstructor
public class CardTemplatePublicController {

    private final CardTemplateService service;
    private final PermissionService permissionService;

    public static class MatchRequest {
        public String objectType;
        public String kind = "LIST";
        public Map<String, Object> fields;
    }

    @PostMapping("/api/campaigns/{campaignId}/cards/match")
    public List<String> match(@PathVariable UUID campaignId,
                              @AuthenticationPrincipal UserPrincipal principal,
                              @RequestBody List<MatchRequest> requests) {
        permissionService.requireMember(campaignId, principal.getId());
        List<String> result = new ArrayList<>();
        for (MatchRequest request : requests) {
            var template = service.match(request.objectType, request.kind, request.fields);
            result.add(template == null ? null : template.getId().toString());
        }
        return result;
    }

    @GetMapping("/api/campaigns/{campaignId}/cards")
    public List<CardTemplate> list(@PathVariable UUID campaignId,
                                   @AuthenticationPrincipal UserPrincipal principal,
                                   @RequestParam(defaultValue = "LIST") String kind) {
        permissionService.requireMember(campaignId, principal.getId());
        return service.all().stream()
                .filter(template -> kind.equals(template.getKind()))
                .collect(java.util.stream.Collectors.toList());
    }
}
