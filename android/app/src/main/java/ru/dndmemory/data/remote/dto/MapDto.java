package ru.dndmemory.data.remote.dto;

import java.util.ArrayList;
import java.util.List;

/** Интерактивная карта кампании. */
public class MapDto {
    public String id;
    public String name;
    public String description;
    public String imageFileId;
    public int markerCount;
    public String updatedAt;
    public List<MapMarkerDto> markers = new ArrayList<>();
}
