package ru.dndmemory.data.remote.dto;

import java.util.ArrayList;
import java.util.List;

/** Элемент списка и детали кампании (docs/api.md, раздел 4). */
public class CampaignDto {

    public String id;
    public String title;
    public String description;
    public String myRole;
    public List<String> myPermissions = new ArrayList<>();
    public int memberCount;
    public int entityCount;
    public int retellingCount;
    public long latestRevision;
    public String updatedAt;
}
