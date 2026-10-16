package ru.dndmemory.data.remote.dto;

/** PATCH /api/campaigns/{id}/members/{memberId} */
public class PatchMemberRequest {

    public String role;
    public String linkedHeroId;

    public PatchMemberRequest(String role, String linkedHeroId) {
        this.role = role;
        this.linkedHeroId = linkedHeroId;
    }
}
