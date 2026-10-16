package ru.dndmemory.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.dndmemory.config.AppProperties;
import ru.dndmemory.service.PasswordResetService;
import ru.dndmemory.web.dto.AdminDtos;

import java.util.List;

/**
 * «Админ-панель» без отдельной роли: доступ даёт только заголовок {@code X-Admin-Token}
 * с значением переменной окружения {@code DND_ADMIN_TOKEN}. Если переменная не задана,
 * эндпоинт не существует (404).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final PasswordResetService passwordResetService;
    private final AppProperties properties;

    @GetMapping("/password-resets")
    public List<AdminDtos.PasswordResetDto> passwordResets(
            @RequestHeader(value = "X-Admin-Token", required = false) String adminToken) {
        String expected = properties.getAdminToken();
        if (expected == null || expected.trim().isEmpty()) {
            throw ApiException.notFound("Админ-панель");
        }
        if (adminToken == null || !expected.equals(adminToken)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Неверный X-Admin-Token");
        }
        return passwordResetService.pending();
    }
}
