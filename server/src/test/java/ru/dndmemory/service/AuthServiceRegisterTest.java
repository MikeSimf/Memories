package ru.dndmemory.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.dndmemory.config.AppProperties;
import ru.dndmemory.domain.RefreshToken;
import ru.dndmemory.domain.User;
import ru.dndmemory.repo.RefreshTokenRepository;
import ru.dndmemory.repo.UserRepository;
import ru.dndmemory.security.JwtService;
import ru.dndmemory.web.ApiException;
import ru.dndmemory.web.dto.AuthDtos;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Регистрация: логин в нижнем регистре, приглашение из QR присоединяет к кампании
 * и гасится, а срок жизни сессии зависит от платформы.
 */
class AuthServiceRegisterTest {

    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private InviteService inviteService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        AppProperties properties = mock(AppProperties.class);
        DtoMapper dtoMapper = mock(DtoMapper.class);
        inviteService = mock(InviteService.class);

        AppProperties.Jwt jwt = new AppProperties.Jwt();
        jwt.setSecret("test-secret-for-register-tests-at-least-32-bytes");
        when(properties.getJwt()).thenReturn(jwt);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.createAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtService.getAccessTtlSeconds()).thenReturn(900L);
        when(dtoMapper.toUser(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return AuthDtos.UserDto.builder().id(user.getId()).login(user.getLogin()).build();
        });

        authService = new AuthService(userRepository, refreshTokenRepository, passwordEncoder,
                jwtService, properties, dtoMapper, inviteService);
    }

    private AuthDtos.RegisterRequest request(String login, String inviteToken, String platform) {
        AuthDtos.RegisterRequest request = new AuthDtos.RegisterRequest();
        request.setLogin(login);
        request.setEmail("GM@Example.com");
        request.setPassword("secret123");
        request.setDisplayName("Мастер");
        request.setInviteToken(inviteToken);
        request.setPlatform(platform);
        return request;
    }

    @Test
    @DisplayName("Регистрация по приглашению: логин в нижнем регистре, приглашение гасится сервисом приглашений")
    void registerWithInviteJoinsCampaign() {
        when(userRepository.existsByLogin("master")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("gm@example.com")).thenReturn(false);

        AuthDtos.TokenResponse tokens = authService.register(request("Master", "ABCDEFGH", "android"));

        assertTrue(tokens.getAccessToken().length() > 0);
        assertTrue(tokens.getRefreshToken().length() > 0);
        assertEquals(900L, tokens.getExpiresIn());

        // приглашение принимается тем же пользователем, которого только что создали
        verify(inviteService).accept(eq("ABCDEFGH"), any());

        var savedUser = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertEquals("master", savedUser.getValue().getLogin());
        assertEquals("gm@example.com", savedUser.getValue().getEmail());
        assertFalse(savedUser.getValue().isEmailVerified());

        var savedToken = org.mockito.ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(savedToken.capture());
        assertEquals("android", savedToken.getValue().getPlatform());
        long days = Duration.between(Instant.now(), savedToken.getValue().getExpiresAt()).toDays();
        assertTrue(days >= 364 && days <= 365, "приложению выдаётся годовая сессия");
    }

    @Test
    @DisplayName("Без приглашения приглашения не трогаем, веб-сессия — 30 дней")
    void registerWithoutInvite() {
        when(userRepository.existsByLogin("master")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("gm@example.com")).thenReturn(false);

        authService.register(request("master", null, null));

        verifyNoInteractions(inviteService);
        var savedToken = org.mockito.ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(savedToken.capture());
        assertEquals("web", savedToken.getValue().getPlatform());
        long days = Duration.between(Instant.now(), savedToken.getValue().getExpiresAt()).toDays();
        assertTrue(days >= 29 && days <= 30);
    }

    @Test
    @DisplayName("Занятый логин — конфликт, пользователь не создаётся")
    void duplicateLoginRejected() {
        when(userRepository.existsByLogin("master")).thenReturn(true);

        ApiException error = assertThrows(ApiException.class, () -> authService.register(request("master", null, null)));
        assertEquals("login_taken", error.getCode());
        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(inviteService);
    }

    @Test
    @DisplayName("Вход не зависит от регистра логина")
    void loginIgnoresCase() {
        User user = new User();
        user.setId(java.util.UUID.randomUUID());
        user.setLogin("master");
        user.setEmail("gm@example.com");
        user.setPasswordHash("hash");
        when(userRepository.findByLogin("master")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "hash")).thenReturn(true);

        AuthDtos.LoginRequest request = new AuthDtos.LoginRequest();
        request.setLogin("MASTER");
        request.setPassword("secret123");
        authService.login(request);

        verify(userRepository).findByLogin("master");
    }
}
