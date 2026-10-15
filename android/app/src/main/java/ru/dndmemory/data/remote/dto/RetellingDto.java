package ru.dndmemory.data.remote.dto;

import java.util.ArrayList;
import java.util.List;

import ru.dndmemory.data.model.EntityLink;

/**
 * Пересказ сессии. Поле {@code status} со стороны клиента всегда
 * {@code PUBLISHED}: {@code DRAFT} сервер отвергает кодом {@code draft_not_synced}.
 */
public class RetellingDto {

    public String id;
    public String campaignId;
    public String clientId;
    public String title;
    public String body;
    public String authorEntityId;
    public String authorEntityName;
    /** yyyy-MM-dd */
    public String sessionDate;
    public String status;
    public List<EntityLink> links = new ArrayList<>();
    public long revision;
    public String createdBy;
    public String updatedBy;
    public String createdAt;
    public String updatedAt;
    public boolean deleted;

    /** Точка отсчёта для PATCH (заголовок If-Match). */
    public transient Long baseRevision;
}
