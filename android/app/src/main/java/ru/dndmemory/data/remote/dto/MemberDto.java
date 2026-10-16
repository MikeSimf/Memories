package ru.dndmemory.data.remote.dto;

/** Участник кампании (docs/api.md, раздел 5): привязка к герою — {@code linkedHeroId}. */
public class MemberDto {

    public String id;
    public String userId;
    public String displayName;
    public String email;
    public String role;
    public String linkedHeroId;
    public String linkedHeroName;
    public String status;
    public String createdAt;
}
