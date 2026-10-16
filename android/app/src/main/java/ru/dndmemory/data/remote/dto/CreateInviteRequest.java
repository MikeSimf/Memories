package ru.dndmemory.data.remote.dto;

/**
 * POST /api/campaigns/{id}/invites.
 * Приглашение всегда одноразовое, поэтому {@code maxUses} в запросе нет.
 */
public class CreateInviteRequest {

    public String role;
    public String linkedHeroId;
    public int expiresInHours;

    public CreateInviteRequest(String role, String linkedHeroId, int expiresInHours) {
        this.role = role;
        this.linkedHeroId = linkedHeroId;
        this.expiresInHours = expiresInHours;
    }

    /** Срок по умолчанию — 7 дней (docs/api.md, раздел 5). */
    public static CreateInviteRequest of(String role, String linkedHeroId) {
        return new CreateInviteRequest(role, linkedHeroId, 168);
    }
}
