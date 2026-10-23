package ru.dndmemory.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.dndmemory.domain.EmailVerificationToken;

import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationRepository extends JpaRepository<EmailVerificationToken, UUID> {
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);
}
