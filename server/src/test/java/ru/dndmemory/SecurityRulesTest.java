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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Правила доступа к API. Проверяем главное: без токена открыты ровно вход,
 * health и регистрация устройства — и ничего больше.
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
    @DisplayName("Регистрация устройства доступна без токена: пропуск — сам код из QR")
    void enrollIsOpenWithoutToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/devices/enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"AAAAAAAA\",\"platform\":\"android\"}"))
                .andReturn();
        // Дальше сервер ответит «приглашение недействительно» или ошибкой базы,
        // но авторизация не должна требоваться
        assertNotEquals(401, status(result), "регистрация устройства не должна требовать вход");
        assertNotEquals(403, status(result));
    }

    @Test
    @DisplayName("Health и версия открыты: по ним проверяют живость сервиса")
    void healthIsOpen() throws Exception {
        assertEquals(200, status(mockMvc.perform(get("/api/health")).andReturn()));
        assertEquals(200, status(mockMvc.perform(get("/api/version")).andReturn()));
    }

    @Test
    @DisplayName("Вход и регистрация открыты, но проверяют тело запроса")
    void authEndpointsAreOpen() throws Exception {
        assertEquals(400, status(mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn()));
        assertEquals(400, status(mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn()));
    }

    @Test
    @DisplayName("Всё остальное без токена закрыто")
    void everythingElseRequiresToken() throws Exception {
        assertEquals(401, status(mockMvc.perform(get("/api/me")).andReturn()));
        assertEquals(401, status(mockMvc.perform(get("/api/campaigns")).andReturn()));
        assertEquals(401, status(mockMvc.perform(get("/api/campaigns/" + UUID + "/entities")).andReturn()));
        // список устройств кампании виден только авторизованному участнику
        assertEquals(401, status(mockMvc.perform(get("/api/campaigns/" + UUID + "/devices")).andReturn()));
        assertEquals(401, status(mockMvc.perform(post("/api/campaigns/" + UUID + "/devices/enrollments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Аня\",\"role\":\"VIEWER\"}")).andReturn()));
        assertEquals(401, status(mockMvc.perform(delete("/api/campaigns/" + UUID + "/devices/" + UUID))
                .andReturn()));
    }

    @Test
    @DisplayName("Предпросмотр приглашения открыт — страница /join работает до входа")
    void invitePreviewIsOpen() throws Exception {
        assertNotEquals(401, status(mockMvc.perform(get("/api/invites/AAAAAAAA")).andReturn()));
    }
}
