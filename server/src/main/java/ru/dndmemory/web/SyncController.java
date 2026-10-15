package ru.dndmemory.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.dndmemory.security.UserPrincipal;
import ru.dndmemory.service.SyncService;
import ru.dndmemory.web.dto.SyncDtos;

import javax.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/api/campaigns/{campaignId}/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;

    /** Отдаёт изменения с ревизией больше since, кроме сделанных этим же устройством. */
    @GetMapping("/changes")
    public SyncDtos.PullResponse pull(@AuthenticationPrincipal UserPrincipal principal,
                                      @PathVariable UUID campaignId,
                                      @RequestParam(defaultValue = "0") long since,
                                      @RequestParam(defaultValue = "500") int limit,
                                      @RequestParam(required = false) String clientId) {
        return syncService.pull(campaignId, principal.getId(), since, limit, clientId);
    }

    @PostMapping("/changes")
    public SyncDtos.PushResponse push(@AuthenticationPrincipal UserPrincipal principal,
                                      @PathVariable UUID campaignId,
                                      @Valid @RequestBody SyncDtos.PushRequest request) {
        return syncService.push(campaignId, principal.getId(), request);
    }

    @GetMapping("/snapshot")
    public SyncDtos.SnapshotDto snapshot(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable UUID campaignId,
                                         @RequestParam(required = false) String cursor,
                                         @RequestParam(defaultValue = "500") int limit) {
        return syncService.snapshot(campaignId, principal.getId(), cursor, limit);
    }

    @GetMapping("/state")
    public SyncDtos.SyncStateDto state(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable UUID campaignId) {
        return syncService.state(campaignId, principal.getId());
    }
}
