package ru.dndmemory.data.remote.dto;

/** POST /api/campaigns */
public class CreateCampaignRequest {

    public String title;
    public String description;

    public CreateCampaignRequest(String title, String description) {
        this.title = title;
        this.description = description;
    }
}
