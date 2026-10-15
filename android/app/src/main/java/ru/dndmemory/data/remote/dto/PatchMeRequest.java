package ru.dndmemory.data.remote.dto;

/** PATCH /api/me */
public class PatchMeRequest {

    public String displayName;
    public String avatarFileId;

    public PatchMeRequest(String displayName, String avatarFileId) {
        this.displayName = displayName;
        this.avatarFileId = avatarFileId;
    }
}
