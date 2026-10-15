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
import ru.dndmemory.service.MemberService;
import ru.dndmemory.web.dto.CampaignDtos;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/campaigns/{campaignId}/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping
    public List<CampaignDtos.MemberDto> list(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable UUID campaignId) {
        return memberService.list(campaignId, principal.getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CampaignDtos.AddMemberResponse add(@AuthenticationPrincipal UserPrincipal principal,
                                              @PathVariable UUID campaignId,
                                              @Valid @RequestBody CampaignDtos.AddMemberRequest request) {
        return memberService.add(campaignId, principal.getId(), request);
    }

    @PatchMapping("/{memberId}")
    public CampaignDtos.MemberDto update(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable UUID campaignId,
                                         @PathVariable UUID memberId,
                                         @RequestBody CampaignDtos.UpdateMemberRequest request) {
        return memberService.update(campaignId, memberId, principal.getId(), request);
    }

    /** Передача владения кампанией другому участнику. */
    @PostMapping("/{memberId}/transfer-ownership")
    public CampaignDtos.MemberDto transferOwnership(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable UUID campaignId,
                                                    @PathVariable UUID memberId) {
        return memberService.transferOwnership(campaignId, memberId, principal.getId());
    }

    @DeleteMapping("/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal UserPrincipal principal,
                       @PathVariable UUID campaignId,
                       @PathVariable UUID memberId) {
        memberService.remove(campaignId, memberId, principal.getId());
    }
}
