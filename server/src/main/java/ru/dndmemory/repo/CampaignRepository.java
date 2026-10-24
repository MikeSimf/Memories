package ru.dndmemory.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.dndmemory.domain.Campaign;

import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {

    Optional<Campaign> findByIdAndDeletedFalse(UUID id);

    List<Campaign> findAllByIdInAndDeletedFalse(List<UUID> ids);

    List<Campaign> findAllByDeletedFalseOrderByUpdatedAtDesc();

    /** Блокировка строки кампании на время изменения: ревизии выдаются строго по порядку. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Campaign c where c.id = :id")
    Optional<Campaign> findForUpdate(@Param("id") UUID id);
}
