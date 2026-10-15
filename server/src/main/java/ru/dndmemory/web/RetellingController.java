package ru.dndmemory.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.dndmemory.security.UserPrincipal;
import ru.dndmemory.service.RetellingService;
import ru.dndmemory.web.dto.RetellingDtos;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/campaigns/{campaignId}/retellings")
@RequiredArgsConstructor
public class RetellingController {

    private final RetellingService retellingService;

    @GetMapping
    public List<RetellingDtos.RetellingDto> list(@AuthenticationPrincipal UserPrincipal principal,
                                                 @PathVariable UUID campaignId,
                                                 @RequestParam(required = false, name = "q") String query,
                                                 @RequestParam(required = false) UUID authorEntityId) {
        return retellingService.list(campaignId, principal.getId(), query, authorEntityId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RetellingDtos.RetellingDto create(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable UUID campaignId,
                                             @Valid @RequestBody RetellingDtos.CreateRetellingRequest request) {
        return retellingService.create(campaignId, principal.getId(), request);
    }

    @GetMapping("/{retellingId}")
    public RetellingDtos.RetellingDto get(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable UUID campaignId,
                                          @PathVariable UUID retellingId) {
        return retellingService.get(campaignId, retellingId, principal.getId());
    }

    @PatchMapping("/{retellingId}")
    public RetellingDtos.RetellingDto update(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable UUID campaignId,
                                             @PathVariable UUID retellingId,
                                             @RequestBody RetellingDtos.UpdateRetellingRequest request) {
        return retellingService.update(campaignId, retellingId, principal.getId(), request);
    }

    @DeleteMapping("/{retellingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal principal,
                       @PathVariable UUID campaignId,
                       @PathVariable UUID retellingId) {
        retellingService.delete(campaignId, retellingId, principal.getId());
    }
}
