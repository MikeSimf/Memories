package ru.dndmemory.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.dndmemory.domain.PasswordResetRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetRepository extends JpaRepository<PasswordResetRequest, UUID> {

    Optional<PasswordResetRequest> findByTokenHash(String tokenHash);

    List<PasswordResetRequest> findAllByUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(Instant now);

    @Modifying
    @Query("update PasswordResetRequest r set r.used = true where r.userId = :userId and r.used = false")
    int consumeAllForUser(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from PasswordResetRequest r where r.expiresAt < :before")
    int deleteExpired(@Param("before") Instant before);
}
