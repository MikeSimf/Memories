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
import ru.dndmemory.service.MaterialService;
import ru.dndmemory.web.dto.MaterialDtos;
import ru.dndmemory.web.dto.ObjectListDto;

import javax.validation.Valid;
import java.time.Instant;
import java.util.UUID;

/** Фото-материалы кампании: карты, портреты, письма. */
@RestController
@RequestMapping("/api/campaigns/{campaignId}/materials")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;

    @GetMapping
    public ObjectListDto<MaterialDtos.MaterialDto> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID campaignId,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant updatedSince,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return materialService.list(campaignId, principal.getId(), query, updatedSince, limit, offset);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MaterialDtos.MaterialDto create(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable UUID campaignId,
                                           @Valid @RequestBody MaterialDtos.CreateMaterialRequest request) {
        return materialService.create(campaignId, principal.getId(), request);
    }

    @GetMapping("/{materialId}")
    public MaterialDtos.MaterialDto get(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable UUID campaignId,
                                        @PathVariable UUID materialId) {
        return materialService.get(campaignId, materialId, principal.getId());
    }

    @PatchMapping("/{materialId}")
    public MaterialDtos.MaterialDto update(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable UUID campaignId,
                                           @PathVariable UUID materialId,
                                           @RequestHeader(name = "If-Match", required = false) Long ifMatch,
                                           @Valid @RequestBody MaterialDtos.UpdateMaterialRequest request) {
        return materialService.update(campaignId, materialId, principal.getId(), request, ifMatch);
    }

    @DeleteMapping("/{materialId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal principal,
                       @PathVariable UUID campaignId,
                       @PathVariable UUID materialId) {
        materialService.delete(campaignId, materialId, principal.getId());
    }
}
