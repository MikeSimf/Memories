package ru.dndmemory.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.dndmemory.security.UserPrincipal;
import ru.dndmemory.service.QuestService;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/campaigns/{campaignId}/quests")
public class QuestController {

    private final QuestService service;

    @GetMapping
    public List<QuestService.QuestDto> list(@PathVariable UUID campaignId,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        return service.list(campaignId, principal.getId());
    }

    @GetMapping("/{questId}")
    public QuestService.QuestDto get(@PathVariable UUID campaignId, @PathVariable UUID questId,
                                     @AuthenticationPrincipal UserPrincipal principal) {
        return service.get(campaignId, questId, principal.getId());
    }

    @PostMapping
    public QuestService.QuestDto create(@PathVariable UUID campaignId,
                                        @AuthenticationPrincipal UserPrincipal principal,
                                        @RequestBody QuestService.QuestRequest request) {
        return service.create(campaignId, principal.getId(), request);
    }

    @PatchMapping("/{questId}")
    public QuestService.QuestDto update(@PathVariable UUID campaignId, @PathVariable UUID questId,
                                        @AuthenticationPrincipal UserPrincipal principal,
                                        @RequestBody QuestService.QuestRequest request) {
        return service.update(campaignId, questId, principal.getId(), request);
    }

    @DeleteMapping("/{questId}")
    public void delete(@PathVariable UUID campaignId, @PathVariable UUID questId,
                       @AuthenticationPrincipal UserPrincipal principal) {
        service.delete(campaignId, questId, principal.getId());
    }

    @PostMapping("/{questId}/comments")
    public QuestService.CommentDto addComment(@PathVariable UUID campaignId, @PathVariable UUID questId,
                                              @AuthenticationPrincipal UserPrincipal principal,
                                              @RequestBody QuestService.CommentRequest request) {
        return service.addComment(campaignId, questId, principal.getId(), request);
    }

    @PatchMapping("/{questId}/comments/{commentId}")
    public void updateComment(@PathVariable UUID campaignId, @PathVariable UUID questId,
                              @PathVariable UUID commentId, @AuthenticationPrincipal UserPrincipal principal,
                              @RequestBody QuestService.CommentRequest request) {
        service.updateComment(campaignId, questId, commentId, principal.getId(), request);
    }

    @DeleteMapping("/{questId}/comments/{commentId}")
    public void deleteComment(@PathVariable UUID campaignId, @PathVariable UUID questId,
                              @PathVariable UUID commentId, @AuthenticationPrincipal UserPrincipal principal) {
        service.deleteComment(campaignId, questId, commentId, principal.getId());
    }
}
