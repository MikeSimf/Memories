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
import ru.dndmemory.service.ItemService;
import ru.dndmemory.web.dto.ItemDtos;
import ru.dndmemory.web.dto.ObjectListDto;

import javax.validation.Valid;
import java.time.Instant;
import java.util.UUID;

/** Предметы: правят владелец, редактор-мастер и редактор-герой. */
@RestController
@RequestMapping("/api/campaigns/{campaignId}/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    @GetMapping
    public ObjectListDto<ItemDtos.ItemDto> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID campaignId,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant updatedSince,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return itemService.list(campaignId, principal.getId(), query, updatedSince, limit, offset);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ItemDtos.ItemDto create(@AuthenticationPrincipal UserPrincipal principal,
                                   @PathVariable UUID campaignId,
                                   @Valid @RequestBody ItemDtos.CreateItemRequest request) {
        return itemService.create(campaignId, principal.getId(), request);
    }

    @GetMapping("/{itemId}")
    public ItemDtos.ItemDto get(@AuthenticationPrincipal UserPrincipal principal,
                                @PathVariable UUID campaignId,
                                @PathVariable UUID itemId) {
        return itemService.get(campaignId, itemId, principal.getId());
    }

    @PatchMapping("/{itemId}")
    public ItemDtos.ItemDto update(@AuthenticationPrincipal UserPrincipal principal,
                                   @PathVariable UUID campaignId,
                                   @PathVariable UUID itemId,
                                   @RequestHeader(value = "If-Match", required = false) Long ifMatchRevision,
                                   @Valid @RequestBody ItemDtos.UpdateItemRequest request) {
        return itemService.update(campaignId, itemId, principal.getId(), request, ifMatchRevision);
    }

    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal principal,
                       @PathVariable UUID campaignId,
                       @PathVariable UUID itemId) {
        itemService.delete(campaignId, itemId, principal.getId());
    }
}
