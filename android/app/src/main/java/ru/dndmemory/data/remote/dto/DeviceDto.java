package ru.dndmemory.data.remote.dto;

/** Устройство кампании (docs/api.md, раздел 3.1). */
public class DeviceDto {

    public String id;
    public String userId;
    public String displayName;
    public String label;
    public String platform;
    public String campaignId;
    public boolean revoked;
    public String createdAt;
    public String lastSeenAt;
}
