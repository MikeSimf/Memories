package ru.dndmemory.domain.extender;

import java.time.Instant;
import java.util.UUID;

/**
 * Участие в офлайн-синхронизации: мягкое удаление, авторство, время и ревизия.
 *
 * <p>Объект с этим контрактом попадает в журнал изменений и умеет разрешать
 * конфликты по времени последней правки (docs/sync.md).</p>
 */
public interface SyncExtended {

    boolean isDeleted();

    void setDeleted(boolean deleted);

    Instant getCreatedAt();

    void setCreatedAt(Instant createdAt);

    Instant getUpdatedAt();

    void setUpdatedAt(Instant updatedAt);

    UUID getCreatedBy();

    void setCreatedBy(UUID createdBy);

    UUID getUpdatedBy();

    void setUpdatedBy(UUID updatedBy);

    long getRevision();

    void setRevision(long revision);
}
