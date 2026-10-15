package ru.dndmemory.data.remote.dto;

/** POST /api/devices/enroll — шаг телефона при регистрации устройства по QR (docs/api.md, 3.1). */
public class EnrollRequest {

    public String token;
    public String label;
    public String platform;

    public EnrollRequest(String token, String label, String platform) {
        this.token = token;
        this.label = label;
        this.platform = platform;
    }
}
