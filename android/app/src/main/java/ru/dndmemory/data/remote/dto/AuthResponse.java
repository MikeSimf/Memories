package ru.dndmemory.data.remote.dto;

/** Ответ register / login / oauth callback. */
public class AuthResponse {

    public UserDto user;
    public String accessToken;
    public String refreshToken;
    public long expiresIn;
}
