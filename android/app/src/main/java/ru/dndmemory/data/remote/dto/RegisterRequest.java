package ru.dndmemory.data.remote.dto;

/** POST /api/auth/register */
public class RegisterRequest {

    public String email;
    public String password;
    public String displayName;

    public RegisterRequest(String email, String password, String displayName) {
        this.email = email;
        this.password = password;
        this.displayName = displayName;
    }
}
