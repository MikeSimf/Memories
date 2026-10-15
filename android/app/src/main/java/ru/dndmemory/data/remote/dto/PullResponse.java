package ru.dndmemory.data.remote.dto;

import java.util.ArrayList;
import java.util.List;

/** GET /api/campaigns/{id}/sync/changes */
public class PullResponse {

    public long since;
    public long latestRevision;
    public boolean hasMore;
    public List<PullChangeDto> changes = new ArrayList<>();
}
