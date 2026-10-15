package ru.dndmemory.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/** Пользователь, извлечённый из access-токена. */
@Getter
@AllArgsConstructor
public class UserPrincipal {

    private final UUID id;
    private final String email;
}
