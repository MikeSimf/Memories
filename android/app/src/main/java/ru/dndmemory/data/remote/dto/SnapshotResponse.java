package ru.dndmemory.data.remote.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * GET /api/campaigns/{id}/sync/snapshot?limit=500&cursor=…
 * Клиент проходит по курсорам, пока {@code nextCursor} не станет null.
 */
public class SnapshotResponse {

    public long latestRevision;
    public String nextCursor;
    public List<EntityDto> entities = new ArrayList<>();
    public List<RetellingDto> retellings = new ArrayList<>();
    public List<MemberDto> members = new ArrayList<>();
    public CampaignDto campaign;

    public boolean hasMore() {
        return nextCursor != null && !nextCursor.isEmpty();
    }
}
