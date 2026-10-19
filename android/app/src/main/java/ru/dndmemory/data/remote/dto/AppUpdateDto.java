package ru.dndmemory.data.remote.dto;

/** GET /api/app/latest — данные для проверки обновления приложения. */
public class AppUpdateDto {

    public int versionCode;
    public String versionName;
    public int minVersionCode;
    public String url;
    public String notes;
}
