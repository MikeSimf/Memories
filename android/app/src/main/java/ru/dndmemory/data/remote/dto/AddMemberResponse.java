package ru.dndmemory.data.remote.dto;

/**
 * Ответ POST /api/campaigns/{id}/members: либо сразу участник, либо приглашение
 * (если пользователя с такой почтой ещё нет).
 */
public class AddMemberResponse {

    public MemberDto member;
    public InviteDto invite;
}
