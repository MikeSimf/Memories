package ru.dndmemory.data.remote.dto;

/** POST /api/campaigns/{id}/devices/enrollments — выпуск QR-кода устройства. */
public class CreateDeviceEnrollmentRequest {

    public String displayName;
    public String role;
    public String linkedEntityId;
    public Integer expiresInHours;
    public Integer maxUses;
    public String label;

    public CreateDeviceEnrollmentRequest(String displayName, String role, String linkedEntityId,
                                         Integer expiresInHours, Integer maxUses, String label) {
        this.displayName = displayName;
        this.role = role;
        this.linkedEntityId = linkedEntityId;
        this.expiresInHours = expiresInHours;
        this.maxUses = maxUses;
        this.label = label;
    }
}
