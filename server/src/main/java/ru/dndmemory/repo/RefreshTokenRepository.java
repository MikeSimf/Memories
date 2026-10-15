package ru.dndmemory.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.dndmemory.domain.RefreshToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByUserIdAndRevokedFalse(UUID userId);

    /** Гасит все сессии конкретного устройства — используется при его отзыве. */
    @Modifying
    @Query("update RefreshToken t set t.revoked = true where t.deviceId = :deviceId and t.revoked = false")
    int revokeByDeviceId(@Param("deviceId") UUID deviceId);

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :before")
    int deleteExpired(@Param("before") Instant before);
}
