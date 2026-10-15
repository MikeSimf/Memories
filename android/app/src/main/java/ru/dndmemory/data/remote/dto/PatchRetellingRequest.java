package ru.dndmemory.data.remote.dto;

/** PATCH /api/campaigns/{id}/retellings/{retellingId} */
public class PatchRetellingRequest {

    public String title;
    public String body;
    public String authorEntityId;
    public String sessionDate;
    public String status;

    public PatchRetellingRequest(String title, String body, String authorEntityId,
                                 String sessionDate, String status) {
        this.title = title;
        this.body = body;
        this.authorEntityId = authorEntityId;
        this.sessionDate = sessionDate;
        this.status = status;
    }
}
