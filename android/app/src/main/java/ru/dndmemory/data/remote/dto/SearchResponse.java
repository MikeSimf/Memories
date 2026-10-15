package ru.dndmemory.data.remote.dto;

import java.util.ArrayList;
import java.util.List;

/** GET /api/campaigns/{id}/search */
public class SearchResponse {

    public int total;
    public List<SearchItemDto> items = new ArrayList<>();
}
