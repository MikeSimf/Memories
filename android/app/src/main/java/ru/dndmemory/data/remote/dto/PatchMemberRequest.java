package ru.dndmemory.data.remote.dto;

/** PATCH /api/campaigns/{id}/members/{memberId} */
public class PatchMemberRequest {

    public String role;
    public String linkedEntityId;

    public PatchMemberRequest(String role, String linkedEntityId) {
        this.role = role;
        this.linkedEntityId = linkedEntityId;
    }
}
