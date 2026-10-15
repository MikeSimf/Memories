package ru.dndmemory.data.remote.dto;

/** POST /api/campaigns/{id}/members */
public class AddMemberRequest {

    public String email;
    public String role;
    public String linkedEntityId;

    public AddMemberRequest(String email, String role, String linkedEntityId) {
        this.email = email;
        this.role = role;
        this.linkedEntityId = linkedEntityId;
    }
}
