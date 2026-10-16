package ru.dndmemory.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.dndmemory.domain.ObjectType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SearchDtos {

    private SearchDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchItemDto {
        /** HERO, CHARACTER, LOCATION, ITEM или RETELLING. */
        private ObjectType kind;
        private UUID id;
        private String title;
        private String snippet;
        private long revision;
        private Instant updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchResponse {
        private long total;
        private List<SearchItemDto> items;
    }
}
