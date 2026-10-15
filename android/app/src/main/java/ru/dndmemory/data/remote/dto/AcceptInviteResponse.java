package ru.dndmemory.data.remote.dto;

/** POST /api/invites/{token}/accept */
public class AcceptInviteResponse {

    public String campaignId;
    public String memberId;
    public String role;
}
