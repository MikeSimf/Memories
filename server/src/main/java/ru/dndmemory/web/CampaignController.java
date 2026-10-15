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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.dndmemory.security.UserPrincipal;
import ru.dndmemory.service.CampaignService;
import ru.dndmemory.web.dto.CampaignDtos;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;

    @GetMapping
    public List<CampaignDtos.CampaignSummaryDto> list(@AuthenticationPrincipal UserPrincipal principal) {
        return campaignService.list(principal.getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CampaignDtos.CampaignDetailDto create(@AuthenticationPrincipal UserPrincipal principal,
                                                 @Valid @RequestBody CampaignDtos.CreateCampaignRequest request) {
        return campaignService.create(principal.getId(), request);
    }

    @GetMapping("/{campaignId}")
    public CampaignDtos.CampaignDetailDto get(@AuthenticationPrincipal UserPrincipal principal,
                                              @PathVariable UUID campaignId) {
        return campaignService.get(campaignId, principal.getId());
    }

    @PatchMapping("/{campaignId}")
    public CampaignDtos.CampaignDetailDto update(@AuthenticationPrincipal UserPrincipal principal,
                                                 @PathVariable UUID campaignId,
                                                 @Valid @RequestBody CampaignDtos.UpdateCampaignRequest request) {
        return campaignService.update(campaignId, principal.getId(), request);
    }

    @DeleteMapping("/{campaignId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID campaignId) {
        campaignService.delete(campaignId, principal.getId());
    }
}
