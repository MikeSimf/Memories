package ru.dndmemory.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.dndmemory.domain.AuthProvider;
import ru.dndmemory.domain.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** Телефон хранится в нормализованном виде (+7XXXXXXXXXX). */
    java.util.Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);

    Optional<User> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
