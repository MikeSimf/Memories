package ru.dndmemory.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.dndmemory.config.AppProperties;
import ru.dndmemory.service.AuthService;
import ru.dndmemory.service.OAuthService;
import ru.dndmemory.web.dto.AuthDtos;

import javax.validation.Valid;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final OAuthService oAuthService;
    private final AppProperties properties;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.TokenResponse register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthDtos.TokenResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthDtos.TokenResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        return authService.refresh(request.getRefreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody AuthDtos.LogoutRequest request) {
        authService.logout(request.getRefreshToken());
    }

    @GetMapping("/providers")
    public AuthDtos.ProvidersResponse providers() {
        return AuthDtos.ProvidersResponse.builder()
                .yandex(authService.providerInfo(properties.getOauth().getYandex(), "yandex"))
                .vk(authService.providerInfo(properties.getOauth().getVk(), "vk"))
                .build();
    }

    /** Шаг 1: клиент открывает этот адрес — сервер перенаправляет на страницу провайдера. */
    @GetMapping("/oauth/{provider}/authorize")
    public ResponseEntity<Void> authorize(@PathVariable String provider,
                                          @RequestParam String redirectUri,
                                          @RequestParam(required = false) String state) {
        assertTrustedRedirect(redirectUri);
        String url = oAuthService.authorizeUrl(provider, redirectUri, state);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    /** Шаг 3 для Android и SPA: клиент сам передаёт полученный код. */
    @PostMapping("/oauth/{provider}/callback")
    public AuthDtos.TokenResponse callback(@PathVariable String provider,
                                           @Valid @RequestBody AuthDtos.OAuthCallbackRequest request) {
        return oAuthService.exchange(provider, request.getCode(), request.getRedirectUri(),
                request.getState(), request.getCodeVerifier());
    }

    /** Шаг 3 для браузера: сервер сам принимает код и возвращает токены во фрагменте адреса. */
    @GetMapping("/oauth/{provider}/callback")
    public ResponseEntity<Void> callbackRedirect(@PathVariable String provider,
                                                 @RequestParam String code,
                                                 @RequestParam String redirectUri,
                                                 @RequestParam(required = false) String state,
                                                 @RequestParam(required = false) String codeVerifier) {
        assertTrustedRedirect(redirectUri);
        AuthDtos.TokenResponse tokens = oAuthService.exchange(provider, code, redirectUri, state, codeVerifier);
        String fragment = "accessToken=" + enc(tokens.getAccessToken())
                + "&refreshToken=" + enc(tokens.getRefreshToken())
                + "&expiresIn=" + tokens.getExpiresIn();
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirectUri + "#" + fragment))
                .build();
    }

    /** Защита от открытого редиректа: пускаем только наш сайт и схему приложения. */
    private void assertTrustedRedirect(String redirectUri) {
        if (redirectUri == null || redirectUri.trim().isEmpty()) {
            throw ApiException.validation("Не передан redirectUri");
        }
        if (redirectUri.startsWith("dndmemory://")) {
            return;
        }
        List<String> allowed = properties.getCorsOrigins();
        String publicUrl = properties.getPublicUrl();
        boolean trusted = false;
        for (String origin : allowed) {
            if (redirectUri.startsWith(origin)) {
                trusted = true;
                break;
            }
        }
        if (!trusted && publicUrl != null && redirectUri.startsWith(publicUrl)) {
            trusted = true;
        }
        if (!trusted) {
            throw ApiException.validation("redirectUri не входит в список разрешённых");
        }
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
