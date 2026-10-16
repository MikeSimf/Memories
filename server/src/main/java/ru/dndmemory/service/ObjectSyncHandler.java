package ru.dndmemory.service;

import ru.dndmemory.domain.Campaign;
import ru.dndmemory.domain.Member;
import ru.dndmemory.domain.ObjectType;
import ru.dndmemory.web.dto.SyncDtos;

import java.util.List;
import java.util.UUID;

/**
 * Класс справочника, умеющий применять правки журнала синхронизации. Реализуется
 * базовым сервисом объектов — благодаря этому {@link SyncService} не знает деталей
 * четырёх классов (docs/redesign-v2.md, раздел 3).
 */
public interface ObjectSyncHandler {

    ObjectType objectType();

    /** Применить одну правку клиента. Строка кампании уже заблокирована вызывающим. */
    SyncDtos.PushResultDto applySync(Campaign campaign, Member member, UUID userId, String clientId,
                                     SyncDtos.PushChangeDto change);

    /** Страница объектов для полной выгрузки кампании. */
    List<?> snapshotPage(UUID campaignId, int offset, int limit, ObjectIndexService.ObjectContext context);

    long count(UUID campaignId);
}
