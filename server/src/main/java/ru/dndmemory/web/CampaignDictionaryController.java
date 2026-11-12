package ru.dndmemory.web;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.dndmemory.security.UserPrincipal;
import ru.dndmemory.service.CampaignDictionaryService;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

/** Настройки справочников кампании. */
@RestController
@RequiredArgsConstructor
public class CampaignDictionaryController {

    private final CampaignDictionaryService service;

    @GetMapping("/api/campaigns/{campaignId}/dictionaries")
    public List<CampaignDictionaryService.Entry> list(@PathVariable UUID campaignId,
                                                      @AuthenticationPrincipal UserPrincipal principal) {
        return service.list(campaignId, principal.getId());
    }

    @Data
    public static class PatchRequest {
        @NotBlank
        private String kind;
        @NotBlank
        private String name;
        private boolean enabled;
    }

    @PatchMapping("/api/campaigns/{campaignId}/dictionaries")
    public void patch(@PathVariable UUID campaignId,
                      @AuthenticationPrincipal UserPrincipal principal,
                      @Valid @RequestBody PatchRequest request) {
        service.setEnabled(campaignId, principal.getId(), request.kind, request.name, request.enabled);
    }
}
