package ru.dndmemory.data.remote.dto;

/** POST /api/campaigns/{id}/invites */
public class CreateInviteRequest {

    public String role;
    public String linkedEntityId;
    public int expiresInHours;
    public int maxUses;

    public CreateInviteRequest(String role, String linkedEntityId, int expiresInHours, int maxUses) {
        this.role = role;
        this.linkedEntityId = linkedEntityId;
        this.expiresInHours = expiresInHours;
        this.maxUses = maxUses;
    }

    public static CreateInviteRequest of(String role, String linkedEntityId) {
        return new CreateInviteRequest(role, linkedEntityId, 168, 1);
    }
}
