package ru.dndmemory.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.dndmemory.domain.Campaign;
import ru.dndmemory.domain.SyncConflict;
import ru.dndmemory.repo.SyncConflictRepository;
import ru.dndmemory.web.dto.SyncDtos;

import java.util.UUID;

/** Единая точка записи конфликтов синхронизации — для сервисов объектов и SyncService. */
@Component
@RequiredArgsConstructor
public class ConflictRecorder {

    private final SyncConflictRepository conflictRepository;

    public void record(Campaign campaign, UUID userId, SyncDtos.PushChangeDto change,
                       String reason, JsonNode serverPayload) {
        SyncConflict conflict = new SyncConflict();
        conflict.setId(UUID.randomUUID());
        conflict.setCampaignId(campaign.getId());
        conflict.setObjectType(change.getObjectType());
        conflict.setObjectId(change.getObjectId());
        conflict.setClientChangeId(change.getClientChangeId());
        conflict.setActorUserId(userId);
        conflict.setReason(reason);
        conflict.setServerPayload(serverPayload == null ? null : serverPayload.toString());
        conflict.setClientPayload(change.getPayload() == null ? null : change.getPayload().toString());
        conflictRepository.save(conflict);
    }
}
