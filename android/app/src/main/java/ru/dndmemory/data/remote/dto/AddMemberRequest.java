package ru.dndmemory.data.remote.dto;

/** POST /api/campaigns/{id}/members */
public class AddMemberRequest {

    public String email;
    public String role;
    public String linkedHeroId;

    public AddMemberRequest(String email, String role, String linkedHeroId) {
        this.email = email;
        this.role = role;
        this.linkedHeroId = linkedHeroId;
    }
}
