package ru.dndmemory.data.remote.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * GET /api/campaigns/{id}/sync/snapshot?limit=500&cursor=…
 * Страницы идут по классам, за одну страницу отдаётся один класс; клиент
 * проходит по курсорам, пока {@code nextCursor} не станет пустым.
 */
public class SnapshotResponse {

    public long latestRevision;
    public String nextCursor;

    public List<HeroDto> heroes = new ArrayList<>();
    public List<CharacterDto> characters = new ArrayList<>();
    public List<LocationDto> locations = new ArrayList<>();
    public List<ItemDto> items = new ArrayList<>();

    public List<MaterialDto> materials = new ArrayList<>();
    public List<RetellingDto> retellings = new ArrayList<>();
    public List<MemberDto> members = new ArrayList<>();
    public CampaignDto campaign;

    public boolean hasMore() {
        return nextCursor != null && !nextCursor.isEmpty();
    }
}
