package ru.dndmemory.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.dndmemory.domain.User;
import ru.dndmemory.repo.UserRepository;

import java.time.Instant;
import java.util.Optional;

/**
 * Служебная учётка «system» с доступом ко всем кампаниям.
 *
 * <p>Создаётся только если задан {@code SYSTEM_PASSWORD}; в участники кампаний
 * не добавляется, поэтому в списках людей её не видно. От её имени работает
 * «Менеджер ссылок».</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemUserInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${dnd.system-password:}")
    private String systemPassword;

    @Override
    @Transactional
    public void run(String... args) {
        if (systemPassword == null || systemPassword.trim().isEmpty()) {
            return;
        }
        Optional<User> existing = userRepository.findByLogin("system");
        User user = existing.orElseGet(User::new);
        if (existing.isEmpty()) {
            user.setId(java.util.UUID.randomUUID());
            user.setLogin("system");
            user.setEmail(null);
            user.setCreatedAt(Instant.now());
            log.info("Создана служебная учётка system");
        }
        user.setDisplayName("Менеджер ссылок");
        user.setPasswordHash(passwordEncoder.encode(systemPassword.trim()));
        user.setSuperuser(true);
        user.setEmailVerified(false);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }
}
