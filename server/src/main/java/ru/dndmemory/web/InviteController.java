package ru.dndmemory.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.dndmemory.security.UserPrincipal;
import ru.dndmemory.service.InviteService;
import ru.dndmemory.web.dto.InviteDtos;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class InviteController {

    private final InviteService inviteService;

    @GetMapping("/api/campaigns/{campaignId}/invites")
    public List<InviteDtos.InviteDto> list(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable UUID campaignId) {
        return inviteService.list(campaignId, principal.getId());
    }

    @PostMapping("/api/campaigns/{campaignId}/invites")
    @ResponseStatus(HttpStatus.CREATED)
    public InviteDtos.InviteDto create(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable UUID campaignId,
                                       @Valid @RequestBody InviteDtos.CreateInviteRequest request) {
        return inviteService.create(campaignId, principal.getId(), request);
    }

    @DeleteMapping("/api/campaigns/{campaignId}/invites/{inviteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal UserPrincipal principal,
                       @PathVariable UUID campaignId,
                       @PathVariable UUID inviteId) {
        inviteService.revoke(campaignId, inviteId, principal.getId());
    }

    /** Публичный предпросмотр — нужен странице /join/<token> до входа в аккаунт. */
    @GetMapping("/api/invites/{token}")
    public InviteDtos.InvitePreviewDto preview(@PathVariable String token) {
        return inviteService.preview(token);
    }

    @PostMapping("/api/invites/{token}/accept")
    public InviteDtos.AcceptInviteResponse accept(@AuthenticationPrincipal UserPrincipal principal,
                                                  @PathVariable String token) {
        return inviteService.accept(token, principal.getId());
    }
}
