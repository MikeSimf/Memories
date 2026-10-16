package ru.dndmemory.data.remote.dto;

/** GET /api/campaigns/{id}/sync/state */
public class SyncStateDto {

    public long latestRevision;
    public long minRetainedRevision;
    public int entityCount;
    public int heroCount;
    public int characterCount;
    public int locationCount;
    public int itemCount;
    public int retellingCount;
    public int memberCount;
    public int pendingConflicts;
}
