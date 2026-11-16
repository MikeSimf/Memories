package ru.dndmemory.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.dndmemory.security.UserPrincipal;
import ru.dndmemory.service.HeroInjuryService;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/campaigns/{campaignId}/heroes/{heroId}/injuries")
public class HeroInjuryController {

    private final HeroInjuryService service;

    @GetMapping
    public List<HeroInjuryService.InjuryDto> list(@PathVariable UUID campaignId, @PathVariable UUID heroId,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return service.list(campaignId, heroId, principal.getId());
    }

    @PostMapping
    public HeroInjuryService.InjuryDto add(@PathVariable UUID campaignId, @PathVariable UUID heroId,
                                           @AuthenticationPrincipal UserPrincipal principal,
                                           @RequestBody HeroInjuryService.InjuryRequest request) {
        return service.add(campaignId, heroId, principal.getId(), request);
    }

    @PatchMapping("/{injuryId}")
    public void update(@PathVariable UUID campaignId, @PathVariable UUID heroId,
                       @PathVariable UUID injuryId, @AuthenticationPrincipal UserPrincipal principal,
                       @RequestBody HeroInjuryService.InjuryRequest request) {
        service.update(campaignId, heroId, injuryId, principal.getId(), request);
    }

    @DeleteMapping("/{injuryId}")
    public void delete(@PathVariable UUID campaignId, @PathVariable UUID heroId,
                       @PathVariable UUID injuryId, @AuthenticationPrincipal UserPrincipal principal) {
        service.delete(campaignId, heroId, injuryId, principal.getId());
    }
}
