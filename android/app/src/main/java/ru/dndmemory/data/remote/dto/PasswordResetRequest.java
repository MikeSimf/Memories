package ru.dndmemory.data.remote.dto;

/** POST /api/auth/password-reset/request — ответ всегда {@code 202}. */
public class PasswordResetRequest {

    public String email;

    public PasswordResetRequest(String email) {
        this.email = email;
    }
}
