package ru.dndmemory.data.remote.dto;

import java.util.List;

/** GET /api/app/versions — история версий приложения. */
public class AppVersionDto {

    public String version;
    public String date;
    public String notes;

    /** Ответ приходит массивом; Gson создаёт список через этот тип. */
    public static class ListResponse extends java.util.ArrayList<AppVersionDto> {
    }
}
