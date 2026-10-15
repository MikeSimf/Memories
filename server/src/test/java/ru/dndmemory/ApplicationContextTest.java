package ru.dndmemory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import ru.dndmemory.service.AuthService;
import ru.dndmemory.service.EntityService;
import ru.dndmemory.service.InviteService;
import ru.dndmemory.service.MemberService;
import ru.dndmemory.service.RetellingService;
import ru.dndmemory.service.SyncService;
import ru.dndmemory.web.CampaignController;
import ru.dndmemory.web.EntityController;
import ru.dndmemory.web.InviteController;
import ru.dndmemory.web.SyncController;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Поднимает весь контекст приложения без PostgreSQL (H2 в режиме совместимости,
 * Flyway выключен). Ловит циклические зависимости, незакрытые бины и неверную привязку
 * настроек — то, что иначе проявилось бы только при развёртывании.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:dndtest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "dnd.jwt.secret=test-secret-for-context-load-at-least-32-bytes-long",
        "dnd.files-dir=${java.io.tmpdir}/dnd-memory-test-files",
        "dnd.cors-origins=http://localhost:5173"
})
class ApplicationContextTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private EntityService entityService;

    @Autowired
    private RetellingService retellingService;

    @Autowired
    private SyncService syncService;

    @Autowired
    private MemberService memberService;

    @Autowired
    private InviteService inviteService;

    @Autowired
    private CampaignController campaignController;

    @Autowired
    private EntityController entityController;

    @Autowired
    private InviteController inviteController;

    @Autowired
    private SyncController syncController;

    @Test
    @DisplayName("Контекст приложения собирается полностью, без циклических зависимостей")
    void contextLoads() {
        assertNotNull(authService);
        assertNotNull(entityService);
        assertNotNull(retellingService);
        assertNotNull(syncService);
        assertNotNull(memberService);
        assertNotNull(inviteService);
        assertNotNull(campaignController);
        assertNotNull(entityController);
        assertNotNull(inviteController);
        assertNotNull(syncController);
    }
}
