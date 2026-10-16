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
import ru.dndmemory.service.LocationService;
import ru.dndmemory.web.dto.LocationDtos;
import ru.dndmemory.web.dto.ObjectListDto;

import javax.validation.Valid;
import java.time.Instant;
import java.util.UUID;

/** Локации: правят владелец, редактор-мастер и редактор-герой. */
@RestController
@RequestMapping("/api/campaigns/{campaignId}/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @GetMapping
    public ObjectListDto<LocationDtos.LocationDto> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID campaignId,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant updatedSince,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return locationService.list(campaignId, principal.getId(), query, updatedSince, limit, offset);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LocationDtos.LocationDto create(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable UUID campaignId,
                                           @Valid @RequestBody LocationDtos.CreateLocationRequest request) {
        return locationService.create(campaignId, principal.getId(), request);
    }

    @GetMapping("/{locationId}")
    public LocationDtos.LocationDto get(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable UUID campaignId,
                                        @PathVariable UUID locationId) {
        return locationService.get(campaignId, locationId, principal.getId());
    }

    @PatchMapping("/{locationId}")
    public LocationDtos.LocationDto update(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable UUID campaignId,
                                           @PathVariable UUID locationId,
                                           @RequestHeader(value = "If-Match", required = false) Long ifMatchRevision,
                                           @Valid @RequestBody LocationDtos.UpdateLocationRequest request) {
        return locationService.update(campaignId, locationId, principal.getId(), request, ifMatchRevision);
    }

    @DeleteMapping("/{locationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal principal,
                       @PathVariable UUID campaignId,
                       @PathVariable UUID locationId) {
        locationService.delete(campaignId, locationId, principal.getId());
    }
}
