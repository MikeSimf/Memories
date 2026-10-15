package ru.dndmemory.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import ru.dndmemory.config.AppProperties;
import ru.dndmemory.domain.AuthProvider;
import ru.dndmemory.web.ApiException;
import ru.dndmemory.web.dto.AuthDtos;

import java.util.UUID;

/**
 * Вход через Яндекс ID и VK ID. Провайдер считается включённым, только если в окружении
 * заданы client id и секрет; иначе фронт показывает кнопку неактивной.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthService {

    private final AppProperties properties;
    private final RestTemplate restTemplate;
    private final AuthService authService;

    public String authorizeUrl(String providerName, String redirectUri, String state) {
        AppProperties.Provider provider = requireProvider(providerName);
        String effectiveState = (state == null || state.trim().isEmpty())
                ? UUID.randomUUID().toString() : state;
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(provider.getAuthorizeUrl())
                .queryParam("client_id", provider.getClientId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("state", effectiveState);
        if ("yandex".equalsIgnoreCase(providerName)) {
            builder.queryParam("scope", "login:email login:info");
        } else {
            builder.queryParam("scope", "email");
        }
        return builder.build().toUriString();
    }

    public AuthDtos.TokenResponse exchange(String providerName, String code, String redirectUri,
                                           String state, String codeVerifier) {
        AppProperties.Provider provider = requireProvider(providerName);
        String accessToken = requestAccessToken(providerName, provider, code, redirectUri, codeVerifier);
        if ("yandex".equalsIgnoreCase(providerName)) {
            return loginWithYandex(accessToken);
        }
        return loginWithVk(provider, accessToken);
    }

    private String requestAccessToken(String providerName, AppProperties.Provider provider, String code,
                                      String redirectUri, String codeVerifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", provider.getClientId());
        if ("yandex".equalsIgnoreCase(providerName)) {
            form.add("client_secret", provider.getClientSecret());
        } else {
            form.add("redirect_uri", redirectUri);
            if (codeVerifier != null && !codeVerifier.trim().isEmpty()) {
                form.add("code_verifier", codeVerifier);
            }
            form.add("device_id", UUID.randomUUID().toString());
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(provider.getTokenUrl(), HttpMethod.POST,
                    new HttpEntity<>(form, headers), JsonNode.class);
            JsonNode body = response.getBody();
            if (body == null || body.path("access_token").isMissingNode()) {
                throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "internal_error",
                        "Провайдер не вернул токен доступа");
            }
            return body.path("access_token").asText();
        } catch (RestClientException e) {
            log.warn("Обмен кода {} не удался: {}", providerName, e.getMessage());
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "internal_error",
                    "Не удалось получить токен у провайдера " + providerName);
        }
    }

    private AuthDtos.TokenResponse loginWithYandex(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "OAuth " + accessToken);
        JsonNode profile = get(properties.getOauth().getYandex().getProfileUrl(), headers);
        String id = profile.path("id").asText(null);
        String email = profile.path("default_email").asText(null);
        if (email == null && profile.path("emails").isArray() && profile.path("emails").size() > 0) {
            email = profile.path("emails").get(0).asText(null);
        }
        String name = profile.path("real_name").asText(null);
        if (name == null) {
            name = profile.path("display_name").asText(null);
        }
        return authService.loginWithProvider(AuthProvider.YANDEX, id, email, name);
    }

    private AuthDtos.TokenResponse loginWithVk(AppProperties.Provider provider, String accessToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", provider.getClientId());
        form.add("access_token", accessToken);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        JsonNode body;
        try {
            body = restTemplate.exchange(provider.getProfileUrl(), HttpMethod.POST,
                    new HttpEntity<>(form, headers), JsonNode.class).getBody();
        } catch (RestClientException e) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "internal_error",
                    "Не удалось получить профиль VK");
        }
        if (body == null) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "internal_error",
                    "VK не вернул профиль");
        }
        JsonNode user = body.has("user") ? body.path("user") : body;
        String id = user.path("user_id").asText(null);
        String email = user.path("email").asText(null);
        String firstName = user.path("first_name").asText("");
        String lastName = user.path("last_name").asText("");
        String name = (firstName + " " + lastName).trim();
        return authService.loginWithProvider(AuthProvider.VK, id, email, name.isEmpty() ? null : name);
    }

    private JsonNode get(String url, HttpHeaders headers) {
        try {
            JsonNode body = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class)
                    .getBody();
            if (body == null) {
                throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "internal_error",
                        "Провайдер вернул пустой профиль");
            }
            return body;
        } catch (RestClientException e) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "internal_error",
                    "Не удалось получить профиль у провайдера");
        }
    }

    private AppProperties.Provider requireProvider(String providerName) {
        AppProperties.Provider provider = properties.getOauth().byName(providerName);
        if (provider == null) {
            throw ApiException.validation("Неизвестный провайдер: " + providerName);
        }
        if (!provider.isConfigured()) {
            throw ApiException.validation("Вход через " + providerName + " не настроен на сервере");
        }
        return provider;
    }
}
