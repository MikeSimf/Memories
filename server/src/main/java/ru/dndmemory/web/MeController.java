package ru.dndmemory.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.dndmemory.domain.User;
import ru.dndmemory.security.UserPrincipal;
import ru.dndmemory.service.AuthService;
import ru.dndmemory.service.DtoMapper;
import ru.dndmemory.web.dto.AuthDtos;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final AuthService authService;
    private final DtoMapper dtoMapper;

    @GetMapping
    public AuthDtos.UserDto me(@AuthenticationPrincipal UserPrincipal principal) {
        return dtoMapper.toUser(authService.requireUser(principal.getId()));
    }

    @PatchMapping
    public AuthDtos.UserDto update(@AuthenticationPrincipal UserPrincipal principal,
                                   @Valid @RequestBody AuthDtos.UpdateProfileRequest request) {
        User user = authService.updateProfile(principal.getId(), request);
        return dtoMapper.toUser(user);
    }
}
