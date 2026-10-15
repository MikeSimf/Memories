package ru.dndmemory.data.remote.dto;

/** POST /api/auth/login */
public class LoginRequest {

    public String email;
    public String password;

    public LoginRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }
}
