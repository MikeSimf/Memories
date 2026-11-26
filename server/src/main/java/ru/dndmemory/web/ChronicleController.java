package ru.dndmemory.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.dndmemory.security.UserPrincipal;
import ru.dndmemory.service.ChronicleService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/campaigns/{campaignId}/chronicles")
@RequiredArgsConstructor
public class ChronicleController {
    private final ChronicleService chronicles;

    @GetMapping
    public List<ChronicleService.ChronicleDto> list(@PathVariable UUID campaignId, @AuthenticationPrincipal UserPrincipal user) {
        return chronicles.list(campaignId, user.getId());
    }

    @GetMapping("/{chronicleId}")
    public ChronicleService.ChronicleDto get(@PathVariable UUID campaignId, @PathVariable UUID chronicleId,
                                             @AuthenticationPrincipal UserPrincipal user) {
        return chronicles.get(campaignId, chronicleId, user.getId());
    }

    @PostMapping
    public ChronicleService.ChronicleDto create(@PathVariable UUID campaignId, @RequestBody ChronicleService.ChronicleRequest request,
                                                @AuthenticationPrincipal UserPrincipal user) {
        return chronicles.create(campaignId, user.getId(), request);
    }

    @PatchMapping("/{chronicleId}")
    public ChronicleService.ChronicleDto update(@PathVariable UUID campaignId, @PathVariable UUID chronicleId,
                                                @RequestBody ChronicleService.ChronicleRequest request,
                                                @AuthenticationPrincipal UserPrincipal user) {
        return chronicles.update(campaignId, chronicleId, user.getId(), request);
    }

    @DeleteMapping("/{chronicleId}")
    public void delete(@PathVariable UUID campaignId, @PathVariable UUID chronicleId,
                       @AuthenticationPrincipal UserPrincipal user) {
        chronicles.delete(campaignId, chronicleId, user.getId());
    }

    @PostMapping("/{chronicleId}/points")
    public ChronicleService.PointDto addPoint(@PathVariable UUID campaignId, @PathVariable UUID chronicleId,
                                              @RequestBody ChronicleService.PointRequest request,
                                              @AuthenticationPrincipal UserPrincipal user) {
        return chronicles.addPoint(campaignId, chronicleId, user.getId(), request);
    }

    @DeleteMapping("/{chronicleId}/points/{pointId}")
    public void deletePoint(@PathVariable UUID campaignId, @PathVariable UUID chronicleId, @PathVariable UUID pointId,
                            @AuthenticationPrincipal UserPrincipal user) {
        chronicles.deletePoint(campaignId, chronicleId, pointId, user.getId());
    }
}
