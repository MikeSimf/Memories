package ru.dndmemory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Правила доступа к API. Без токена открыты ровно вход (с регистрацией и сбросом
 * пароля), health, версия, предпросмотр приглашения и админ-эндпоинт сброса
 * (его защищает заголовок X-Admin-Token, а не сессия) — и больше ничего.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:dndsec;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "dnd.jwt.secret=test-secret-for-security-rules-at-least-32-bytes",
        "dnd.files-dir=${java.io.tmpdir}/dnd-memory-test-files",
        "dnd.cors-origins=http://localhost:5173",
        // Базы в этом тесте нет: проверяем только правила доступа, логи об ошибках не нужны
        "logging.level.org.hibernate=OFF",
        "logging.level.ru.dndmemory=OFF",
        "logging.level.org.springframework.orm=OFF"
})
class SecurityRulesTest {

    private static final String UUID = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mockMvc;

    private int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    @Test
    @DisplayName("Health и версия открыты: по ним проверяют живость сервиса")
    void healthIsOpen() throws Exception {
        assertEquals(200, status(mockMvc.perform(get("/api/health")).andReturn()));
        assertEquals(200, status(mockMvc.perform(get("/api/version")).andReturn()));
    }

    @Test
    @DisplayName("Вход, регистрация и сброс пароля открыты, но проверяют тело запроса")
    void authEndpointsAreOpen() throws Exception {
        assertEquals(400, status(mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn()));
        assertEquals(400, status(mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn()));
        assertEquals(400, status(mockMvc.perform(post("/api/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn()));
        assertEquals(400, status(mockMvc.perform(post("/api/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn()));
    }

    @Test
    @DisplayName("Провайдеров больше нет: эндпоинты OAuth удалены")
    void oauthEndpointsAreGone() throws Exception {
        // /api/auth/** открыт, но обработчика нет — значит 404, а не вход через провайдера
        assertEquals(404, status(mockMvc.perform(get("/api/auth/providers")).andReturn()));
        assertEquals(404, status(mockMvc.perform(get("/api/auth/oauth/yandex/authorize")
                .param("redirectUri", "dndmemory://oauth")).andReturn()));
    }

    @Test
    @DisplayName("Все четыре набора объектов без токена закрыты")
    void objectEndpointsRequireToken() throws Exception {
        assertEquals(401, status(mockMvc.perform(get("/api/me")).andReturn()));
        assertEquals(401, status(mockMvc.perform(get("/api/campaigns")).andReturn()));
        assertEquals(401, status(mockMvc.perform(get("/api/campaigns/" + UUID + "/heroes")).andReturn()));
        assertEquals(401, status(mockMvc.perform(get("/api/campaigns/" + UUID + "/characters")).andReturn()));
        assertEquals(401, status(mockMvc.perform(get("/api/campaigns/" + UUID + "/locations")).andReturn()));
        assertEquals(401, status(mockMvc.perform(get("/api/campaigns/" + UUID + "/items")).andReturn()));
        assertEquals(401, status(mockMvc.perform(get("/api/campaigns/" + UUID + "/search")
                .param("q", "лира")).andReturn()));
        // старый общий /entities удалён вместе с EntityController
        assertEquals(401, status(mockMvc.perform(get("/api/campaigns/" + UUID + "/entities")).andReturn()));
    }

    @Test
    @DisplayName("Предпросмотр приглашения открыт — страница /join работает до входа")
    void invitePreviewIsOpen() throws Exception {
        assertNotEquals(401, status(mockMvc.perform(get("/api/invites/AAAAAAAA")).andReturn()));
    }

    @Test
    @DisplayName("Админ-панель сброса пароля без DND_ADMIN_TOKEN не существует")
    void adminPanelIsAbsentWithoutToken() throws Exception {
        assertEquals(404, status(mockMvc.perform(get("/api/admin/password-resets")).andReturn()));
        assertEquals(404, status(mockMvc.perform(get("/api/admin/password-resets")
                .header("X-Admin-Token", "whatever")).andReturn()));
    }
}
