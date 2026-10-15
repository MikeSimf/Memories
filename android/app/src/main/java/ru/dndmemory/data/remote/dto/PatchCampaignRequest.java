package ru.dndmemory.data.remote.dto;

/** PATCH /api/campaigns/{id} */
public class PatchCampaignRequest {

    public String title;
    public String description;

    public PatchCampaignRequest(String title, String description) {
        this.title = title;
        this.description = description;
    }
}
