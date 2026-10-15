package ru.dndmemory.data.remote.dto;

/** POST /api/campaigns/{id}/retellings */
public class CreateRetellingRequest {

    public String id;
    public String clientId;
    public String title;
    public String body;
    public String authorEntityId;
    public String sessionDate;
    public String status;

    public CreateRetellingRequest(String id, String clientId, String title, String body,
                                  String authorEntityId, String sessionDate, String status) {
        this.id = id;
        this.clientId = clientId;
        this.title = title;
        this.body = body;
        this.authorEntityId = authorEntityId;
        this.sessionDate = sessionDate;
        this.status = status;
    }
}
