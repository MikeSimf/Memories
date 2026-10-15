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
import ru.dndmemory.domain.EntityType;
import ru.dndmemory.security.UserPrincipal;
import ru.dndmemory.service.EntityService;
import ru.dndmemory.web.dto.EntityDtos;

import javax.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/campaigns/{campaignId}/entities")
@RequiredArgsConstructor
public class EntityController {

    private final EntityService entityService;

    @GetMapping
    public EntityDtos.EntityListDto list(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable UUID campaignId,
                                         @RequestParam(required = false) EntityType type,
                                         @RequestParam(required = false, name = "q") String query,
                                         @RequestParam(required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant updatedSince,
                                         @RequestParam(defaultValue = "50") int limit,
                                         @RequestParam(defaultValue = "0") int offset) {
        return entityService.list(campaignId, principal.getId(), type, query, updatedSince, limit, offset);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EntityDtos.EntityDto create(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable UUID campaignId,
                                       @Valid @RequestBody EntityDtos.CreateEntityRequest request) {
        return entityService.create(campaignId, principal.getId(), request);
    }

    @GetMapping("/{entityId}")
    public EntityDtos.EntityDto get(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable UUID campaignId,
                                    @PathVariable UUID entityId) {
        return entityService.get(campaignId, entityId, principal.getId());
    }

    @PatchMapping("/{entityId}")
    public EntityDtos.EntityDto update(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable UUID campaignId,
                                       @PathVariable UUID entityId,
                                       @RequestHeader(value = "If-Match", required = false) Long ifMatchRevision,
                                       @RequestBody EntityDtos.UpdateEntityRequest request) {
        return entityService.update(campaignId, entityId, principal.getId(), request, ifMatchRevision);
    }

    @DeleteMapping("/{entityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal principal,
                       @PathVariable UUID campaignId,
                       @PathVariable UUID entityId) {
        entityService.delete(campaignId, entityId, principal.getId());
    }

    @GetMapping("/{entityId}/backlinks")
    public List<EntityDtos.BacklinkDto> backlinks(@AuthenticationPrincipal UserPrincipal principal,
                                                  @PathVariable UUID campaignId,
                                                  @PathVariable UUID entityId) {
        return entityService.backlinks(campaignId, entityId, principal.getId());
    }
}
