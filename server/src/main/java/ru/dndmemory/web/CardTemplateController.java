package ru.dndmemory.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.dndmemory.config.AppProperties;
import ru.dndmemory.domain.CardTemplate;
import ru.dndmemory.repo.UserRepository;
import ru.dndmemory.security.UserPrincipal;
import ru.dndmemory.service.CardTemplateService;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

/** Конструктор карточек: доступен только system/админу. */
@RestController
@RequestMapping("/api/admin/cards")
@RequiredArgsConstructor
public class CardTemplateController {

    private final CardTemplateService service;
    private final UserRepository userRepository;
    private final AppProperties properties;

    @GetMapping
    public List<CardTemplate> list(@AuthenticationPrincipal UserPrincipal principal) {
        requireAdmin(principal);
        return service.all();
    }

    @PostMapping
    public CardTemplate save(@AuthenticationPrincipal UserPrincipal principal,
                             @Valid @RequestBody CardTemplateService.SaveRequest request) {
        requireAdmin(principal);
        return service.save(request);
    }

    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        requireAdmin(principal);
        service.delete(id);
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
        return admin != null && !admin.isBlank() && admin.trim().equalsIgnoreCase(email);
    }
}
