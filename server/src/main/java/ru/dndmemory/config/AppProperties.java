package ru.dndmemory.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** Настройки приложения из application.yml (префикс dnd). */
@Data
@ConfigurationProperties(prefix = "dnd")
public class AppProperties {

    private String version = "1.0.0";
    private String publicUrl = "http://localhost:8080";
    private String filesDir = "/var/lib/dnd-memory/files";
    private List<String> corsOrigins = new ArrayList<>();

    /**
     * Токен админ-панели сброса пароля. Если не задан, {@code GET /api/admin/password-resets}
     * отвечает 404 — отдельной роли администратора в проекте нет.
     */
    private String adminToken;

    private App app = new App();
    private Retention retention = new Retention();
    private Jwt jwt = new Jwt();

    /**
     * Данные для проверки обновлений мобильного приложения
     * ({@code GET /api/app/latest}).
     */
    @Data
    public static class App {
        /** Последняя версия: её предлагаем поставить. */
        private int latestVersionCode = 1;
        private String latestVersionName = "1.0.0";
        /** Ниже этой версии работа не поддерживается — обновление обязательное. */
        private int minVersionCode = 1;
        /** Прямая ссылка на apk. */
        private String apkUrl;
        private String notes;
    }

    @Data
    public static class Retention {
        private int changeLogDays = 90;
        private int changeLogMaxRows = 50000;
    }

    @Data
    public static class Jwt {
        private String secret;
        private int accessTokenMinutes = 15;
        /** Веб-сессия: месяц. */
        private int refreshTokenDays = 30;
        /** Сессия приложения: год, чтобы телефон не разлогинивался. */
        private int androidRefreshTokenDays = 365;
    }
}
