package ru.dndmemory.web;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.dndmemory.security.UserPrincipal;
import ru.dndmemory.service.HeroService;
import ru.dndmemory.web.dto.HeroDtos;
import ru.dndmemory.web.dto.ObjectListDto;

import javax.validation.Valid;
import java.time.Instant;
import java.util.UUID;

/** Герои партии: создаёт, правит и удаляет только владелец кампании. */
@RestController
@RequestMapping("/api/campaigns/{campaignId}/heroes")
@RequiredArgsConstructor
public class HeroController {

    private final HeroService heroService;

    @GetMapping
    public ObjectListDto<HeroDtos.HeroDto> list(@AuthenticationPrincipal UserPrincipal principal,
                                                           @PathVariable UUID campaignId,
                                                           @RequestParam(required = false, name = "q") String query,
                                                           @RequestParam(required = false)
                                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                           Instant updatedSince,
                                                           @RequestParam(defaultValue = "50") int limit,
                                                           @RequestParam(defaultValue = "0") int offset) {
        return heroService.list(campaignId, principal.getId(), query, updatedSince, limit, offset);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HeroDtos.HeroDto create(@AuthenticationPrincipal UserPrincipal principal,
                                   @PathVariable UUID campaignId,
                                   @Valid @RequestBody HeroDtos.CreateHeroRequest request) {
        return heroService.create(campaignId, principal.getId(), request);
    }

    @GetMapping("/{heroId}")
    public HeroDtos.HeroDto get(@AuthenticationPrincipal UserPrincipal principal,
                                @PathVariable UUID campaignId,
                                @PathVariable UUID heroId) {
        return heroService.get(campaignId, heroId, principal.getId());
    }

    @PatchMapping("/{heroId}")
    public HeroDtos.HeroDto update(@AuthenticationPrincipal UserPrincipal principal,
                                   @PathVariable UUID campaignId,
                                   @PathVariable UUID heroId,
                                   @RequestHeader(value = "If-Match", required = false) Long ifMatchRevision,
                                   @Valid @RequestBody HeroDtos.UpdateHeroRequest request) {
        return heroService.update(campaignId, heroId, principal.getId(), request, ifMatchRevision);
    }

    @DeleteMapping("/{heroId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal principal,
                       @PathVariable UUID campaignId,
                       @PathVariable UUID heroId) {
        heroService.delete(campaignId, heroId, principal.getId());
    }
}
