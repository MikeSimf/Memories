package ru.dndmemory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.dndmemory.domain.Campaign;
import ru.dndmemory.domain.ChangeLog;
import ru.dndmemory.domain.ChangeOp;
import ru.dndmemory.domain.ObjectType;
import ru.dndmemory.repo.ChangeLogRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Выдаёт ревизии и пишет журнал изменений. Вызывается только внутри транзакции,
 * где строка кампании уже заблокирована (CampaignRepository.findForUpdate).
 */
@Component
@RequiredArgsConstructor
public class ChangeRecorder {

    private final ChangeLogRepository changeLogRepository;
    private final ObjectMapper objectMapper;

    public long record(Campaign campaign, ObjectType objectType, UUID objectId, ChangeOp op, Object payload,
                       UUID actorUserId, String clientChangeId, String deviceId) {
        return record(campaign, objectType, objectId, op, () -> payload, actorUserId, clientChangeId, deviceId);
    }

    /**
     * Вариант с отложенным payload: он собирается ПОСЛЕ увеличения ревизии, поэтому
     * в запись журнала попадает уже новое значение (иначе, например, payload кампании
     * содержал бы revision предыдущего изменения).
     */
    public long record(Campaign campaign, ObjectType objectType, UUID objectId, ChangeOp op,
                       java.util.function.Supplier<Object> payload, UUID actorUserId,
                       String clientChangeId, String deviceId) {
        long revision = campaign.getRevision() + 1;
        campaign.setRevision(revision);
        campaign.touch();

        ChangeLog entry = new ChangeLog();
        entry.setId(UUID.randomUUID());
        entry.setCampaignId(campaign.getId());
        entry.setRevision(revision);
        entry.setObjectType(objectType);
        entry.setObjectId(objectId);
        entry.setOp(op);
        entry.setPayload(toJson(payload.get()));
        entry.setActorUserId(actorUserId);
        entry.setClientChangeId(clientChangeId);
        entry.setDeviceId(deviceId);
        changeLogRepository.save(entry);
        return revision;
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать объект журнала", e);
        }
    }

    /** Надгробие для журнала: объект удалён, развёрнутых данных в записи нет. */
    public Map<String, Object> tombstone(UUID id, ObjectType type, long revision, Instant updatedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        payload.put("objectType", type);
        payload.put("deleted", true);
        payload.put("revision", revision);
        payload.put("updatedAt", updatedAt);
        return payload;
    }
}
