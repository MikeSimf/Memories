package ru.dndmemory.web;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.dndmemory.config.AppProperties;
import ru.dndmemory.domain.CrashReport;
import ru.dndmemory.repo.CrashReportRepository;
import ru.dndmemory.repo.UserRepository;
import ru.dndmemory.security.UserPrincipal;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Приём отчётов о падении приложения и просмотр их админом. */
@RestController
@RequiredArgsConstructor
public class CrashController {

    private final CrashReportRepository repository;
    private final UserRepository userRepository;
    private final AppProperties properties;

    @Data
    public static class CrashRequest {
        @NotBlank
        @Size(max = 20000)
        private String stacktrace;
        @Size(max = 40)
        private String appVersion;
        @Size(max = 200)
        private String device;
    }

    @PostMapping("/api/crashes")
    @ResponseStatus(HttpStatus.CREATED)
    public void report(@AuthenticationPrincipal UserPrincipal principal,
                       @Valid @RequestBody CrashRequest request) {
        CrashReport report = new CrashReport();
        report.setId(UUID.randomUUID());
        report.setUserId(principal == null ? null : principal.getId());
        report.setAppVersion(request.getAppVersion());
        report.setDevice(request.getDevice());
        report.setStacktrace(request.getStacktrace());
        report.setCreatedAt(Instant.now());
        repository.save(report);
    }

    private boolean isAdminEmail(ru.dndmemory.domain.User user) {
        String admin = properties.getAdminEmail();
        return admin != null && !admin.isEmpty()
                && admin.trim().equalsIgnoreCase(user.getEmail());
    }

    @GetMapping("/api/admin/crashes")
    public List<CrashReport> list(@AuthenticationPrincipal UserPrincipal principal) {
        boolean allowed = principal != null
                && userRepository.findById(principal.getId())
                .map(user -> user.isSuperuser() || isAdminEmail(user))
                .orElse(false);
        if (!allowed) {
            throw ApiException.forbidden("Нет доступа");
        }
        return repository.findAllByOrderByCreatedAtDesc();
    }
}
