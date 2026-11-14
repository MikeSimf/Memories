package ru.dndmemory.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.dndmemory.security.UserPrincipal;
import ru.dndmemory.service.MapService;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/campaigns/{campaignId}/maps")
public class MapController {

    private final MapService service;

    @GetMapping
    public List<MapService.MapDto> list(@PathVariable UUID campaignId,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        return service.list(campaignId, principal.getId());
    }

    @GetMapping("/{mapId}")
    public MapService.MapDetailDto get(@PathVariable UUID campaignId, @PathVariable UUID mapId,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        return service.get(campaignId, mapId, principal.getId());
    }

    @PostMapping
    public MapService.MapDetailDto create(@PathVariable UUID campaignId,
                                          @AuthenticationPrincipal UserPrincipal principal,
                                          @RequestBody MapService.MapRequest request) {
        return service.create(campaignId, principal.getId(), request);
    }

    @PatchMapping("/{mapId}")
    public MapService.MapDetailDto update(@PathVariable UUID campaignId, @PathVariable UUID mapId,
                                          @AuthenticationPrincipal UserPrincipal principal,
                                          @RequestBody MapService.MapRequest request) {
        return service.update(campaignId, mapId, principal.getId(), request);
    }

    @DeleteMapping("/{mapId}")
    public void delete(@PathVariable UUID campaignId, @PathVariable UUID mapId,
                       @AuthenticationPrincipal UserPrincipal principal) {
        service.delete(campaignId, mapId, principal.getId());
    }

    @PostMapping("/{mapId}/markers")
    public void addMarker(@PathVariable UUID campaignId, @PathVariable UUID mapId,
                          @AuthenticationPrincipal UserPrincipal principal,
                          @RequestBody MapService.MarkerRequest request) {
        service.addMarker(campaignId, mapId, principal.getId(), request);
    }

    @DeleteMapping("/{mapId}/markers/{markerId}")
    public void deleteMarker(@PathVariable UUID campaignId, @PathVariable UUID mapId,
                             @PathVariable UUID markerId,
                             @AuthenticationPrincipal UserPrincipal principal) {
        service.deleteMarker(campaignId, mapId, markerId, principal.getId());
    }
}
