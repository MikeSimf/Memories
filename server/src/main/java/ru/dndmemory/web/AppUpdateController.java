package ru.dndmemory.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.dndmemory.config.AppProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Данные для проверки обновлений приложения (docs/api.md, раздел 11).
 *
 * <p>Отвечает без авторизации: телефон должен уметь проверить версию даже
 * с истёкшей сессией. Приложение сравнивает свой {@code versionCode}:
 * ниже {@code minVersionCode} — обновление обязательное, иначе — предложение.
 */
@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class AppUpdateController {

    private final AppProperties properties;

    @GetMapping("/latest")
    public Map<String, Object> latest() {
        AppProperties.App app = properties.getApp();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("versionCode", app.getLatestVersionCode());
        body.put("versionName", app.getLatestVersionName());
        body.put("minVersionCode", app.getMinVersionCode());
        body.put("url", app.getApkUrl());
        body.put("notes", app.getNotes());
        return body;
    }

    /** История версий: список описаний, новые сверху. */
    @GetMapping("/versions")
    public List<?> versions() {
        return properties.getApp().getVersions();
    }
}
