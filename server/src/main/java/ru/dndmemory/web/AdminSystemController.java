package ru.dndmemory.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final ru.dndmemory.service.GlobalDictionaryService dictionaries;
    private final JdbcTemplate jdbc;

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

    @GetMapping("/dictionaries")
    public java.util.Map<String, java.util.List<ru.dndmemory.service.GlobalDictionaryService.Item>> dictionaries(
            @AuthenticationPrincipal UserPrincipal principal) {
        requireAdmin(principal);
        return dictionaries.all();
    }

    public static class DictionaryRequest {
        public String kind;
        public String name;
    }

    @PostMapping("/dictionaries")
    public void addDictionary(@AuthenticationPrincipal UserPrincipal principal,
                              @RequestBody DictionaryRequest request) {
        requireAdmin(principal);
        dictionaries.add(request.kind, request.name);
    }

    @DeleteMapping("/dictionaries")
    public void removeDictionary(@AuthenticationPrincipal UserPrincipal principal,
                                 @RequestParam String kind, @RequestParam String name) {
        requireAdmin(principal);
        dictionaries.remove(kind, name);
    }

    public static class IconRequest {
        public String kind;
        public String name;
        public String iconFileId;
    }

    public static class RenameRequest {
        public String kind;
        public String oldName;
        public String newName;
    }

    @PostMapping("/dictionaries/rename")
    public void renameDictionary(@AuthenticationPrincipal UserPrincipal principal,
                                 @RequestBody RenameRequest request) {
        requireAdmin(principal);
        dictionaries.rename(request.kind, request.oldName, request.newName);
    }

    @PostMapping("/dictionaries/icon")
    public void setDictionaryIcon(@AuthenticationPrincipal UserPrincipal principal,
                                  @RequestBody IconRequest request) {
        requireAdmin(principal);
        dictionaries.setIcon(request.kind, request.name, request.iconFileId);
    }

    @DeleteMapping("/feedback/{id}")
    public void deleteFeedback(@AuthenticationPrincipal UserPrincipal principal, @PathVariable java.util.UUID id) {
        requireAdmin(principal);
        jdbc.update("delete from feedback where id = ?", id);
    }

    @DeleteMapping("/crashes/{id}")
    public void deleteCrash(@AuthenticationPrincipal UserPrincipal principal, @PathVariable java.util.UUID id) {
        requireAdmin(principal);
        jdbc.update("delete from crash_reports where id = ?", id);
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
