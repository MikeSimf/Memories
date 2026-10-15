package ru.dndmemory.data.remote.dto;

/** GET /api/campaigns/{id}/sync/state */
public class SyncStateDto {

    public long latestRevision;
    public int entityCount;
    public int retellingCount;
    public int memberCount;
}
