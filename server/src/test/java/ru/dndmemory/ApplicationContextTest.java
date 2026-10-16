package ru.dndmemory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import ru.dndmemory.service.AuthService;
import ru.dndmemory.service.CharacterService;
import ru.dndmemory.service.HeroService;
import ru.dndmemory.service.InviteService;
import ru.dndmemory.service.ItemService;
import ru.dndmemory.service.LocationService;
import ru.dndmemory.service.MemberService;
import ru.dndmemory.service.PasswordResetService;
import ru.dndmemory.service.RetellingService;
import ru.dndmemory.service.SyncService;
import ru.dndmemory.web.AdminController;
import ru.dndmemory.web.CampaignController;
import ru.dndmemory.web.CharacterController;
import ru.dndmemory.web.HeroController;
import ru.dndmemory.web.InviteController;
import ru.dndmemory.web.ItemController;
import ru.dndmemory.web.LocationController;
import ru.dndmemory.web.SearchController;
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
    private PasswordResetService passwordResetService;

    @Autowired
    private HeroService heroService;

    @Autowired
    private CharacterService characterService;

    @Autowired
    private LocationService locationService;

    @Autowired
    private ItemService itemService;

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
    private HeroController heroController;

    @Autowired
    private CharacterController characterController;

    @Autowired
    private LocationController locationController;

    @Autowired
    private ItemController itemController;

    @Autowired
    private InviteController inviteController;

    @Autowired
    private SearchController searchController;

    @Autowired
    private SyncController syncController;

    @Autowired
    private AdminController adminController;

    @Test
    @DisplayName("Контекст приложения собирается полностью, без циклических зависимостей")
    void contextLoads() {
        assertNotNull(authService);
        assertNotNull(passwordResetService);
        assertNotNull(heroService);
        assertNotNull(characterService);
        assertNotNull(locationService);
        assertNotNull(itemService);
        assertNotNull(retellingService);
        assertNotNull(syncService);
        assertNotNull(memberService);
        assertNotNull(inviteService);
        assertNotNull(campaignController);
        assertNotNull(heroController);
        assertNotNull(characterController);
        assertNotNull(locationController);
        assertNotNull(itemController);
        assertNotNull(inviteController);
        assertNotNull(searchController);
        assertNotNull(syncController);
        assertNotNull(adminController);
    }
}
