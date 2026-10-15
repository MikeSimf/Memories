package ru.dndmemory.data.remote.dto;

/** POST /api/me/password — задать или сменить пароль. */
public class SetPasswordRequest {

    /** Обязателен, только если пароль уже задан ({@code hasPassword: true}). */
    public String currentPassword;

    public String newPassword;

    public SetPasswordRequest(String currentPassword, String newPassword) {
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
    }
}
