package ru.dndmemory.data.remote.dto;

/**
 * Ответ {@code POST /api/devices/enroll}: сессия устройства плюс кампания и роль,
 * которые выдал сервер (docs/api.md, раздел 3.1).
 */
public class EnrollResponse {

    public UserDto user;
    public String accessToken;
    public String refreshToken;
    public long expiresIn;

    /** Серверная запись устройства — по ней админ может отозвать доступ. */
    public String deviceId;

    public String campaignId;
    public String campaignTitle;
    public String myRole;
}
