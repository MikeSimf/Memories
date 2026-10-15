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
    private Retention retention = new Retention();
    private Jwt jwt = new Jwt();
    private Oauth oauth = new Oauth();

    @Data
    public static class Retention {
        private int changeLogDays = 90;
        private int changeLogMaxRows = 50000;
    }

    @Data
    public static class Jwt {
        private String secret;
        private int accessTokenMinutes = 15;
        private int refreshTokenDays = 30;
    }

    @Data
    public static class Oauth {
        private Provider yandex = new Provider();
        private Provider vk = new Provider();

        public Provider byName(String name) {
            if ("yandex".equalsIgnoreCase(name)) {
                return yandex;
            }
            if ("vk".equalsIgnoreCase(name)) {
                return vk;
            }
            return null;
        }
    }

    @Data
    public static class Provider {
        private String clientId;
        private String clientSecret;
        private String authorizeUrl;
        private String tokenUrl;
        private String profileUrl;

        public boolean isConfigured() {
            return clientId != null && !clientId.trim().isEmpty()
                    && clientSecret != null && !clientSecret.trim().isEmpty();
        }
    }
}
