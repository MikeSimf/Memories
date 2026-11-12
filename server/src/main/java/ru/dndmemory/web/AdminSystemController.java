package ru.dndmemory.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.dndmemory.config.AppProperties;
import ru.dndmemory.repo.UserRepository;
import ru.dndmemory.security.UserPrincipal;
import ru.dndmemory.service.PasswordResetService;
import ru.dndmemory.web.dto.AdminDtos;

import javax.validation.Valid;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

/** Админ-действия служебной учётки system: ссылки на сброс пароля. */
@RestController
@RequestMapping("/api/admin/system")
@RequiredArgsConstructor
public class AdminSystemController {

    private final PasswordResetService passwordResetService;
    private final UserRepository userRepository;
    private final AppProperties properties;

    @GetMapping("/users")
    public java.util.List<java.util.Map<String, Object>> users(
            @AuthenticationPrincipal UserPrincipal principal) {
        requireAdmin(principal);
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (ru.dndmemory.domain.User user : userRepository.findAll()) {
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("id", user.getId());
            row.put("login", user.getLogin());
            row.put("displayName", user.getDisplayName());
            row.put("email", user.getEmail());
            row.put("superuser", user.isSuperuser());
            row.put("createdAt", user.getCreatedAt());
            row.put("updatedAt", user.getUpdatedAt());
            result.add(row);
        }
        return result;
    }

    @GetMapping("/password-resets")
    public List<AdminDtos.PasswordResetDto> pending(@AuthenticationPrincipal UserPrincipal principal) {
        requireAdmin(principal);
        return passwordResetService.pending();
    }

    @PostMapping("/password-resets")
    public Map<String, String> create(@AuthenticationPrincipal UserPrincipal principal,
                                     @Valid @RequestBody CreateLink request) {
        requireAdmin(principal);
        return Map.of("url", passwordResetService.createLink(request.email));
    }

    private void requireAdmin(UserPrincipal principal) {
        boolean allowed = principal != null && userRepository.findById(principal.getId())
                .map(user -> user.isSuperuser() || adminEmail(user.getEmail()))
                .orElse(false);
        if (!allowed) {
            throw ApiException.forbidden("Нет доступа");
        }
    }

    private boolean adminEmail(String email) {
        String admin = properties.getAdminEmail();
        return admin != null && !admin.trim().isEmpty()
                && admin.trim().equalsIgnoreCase(email);
    }

    public static class CreateLink {
        @NotBlank
        @Email
        public String email;
    }
}
