package ru.dndmemory.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.dndmemory.domain.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** Логин хранится в нижнем регистре, поэтому поиск точный. */
    Optional<User> findByLogin(String login);

    boolean existsByLogin(String login);

    /** Телефон хранится в нормализованном виде (+7XXXXXXXXXX). */
    Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);
}
