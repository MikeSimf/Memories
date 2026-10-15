package ru.dndmemory.data.remote.dto;

/** Тело ошибки: {@code { "error": { "code", "message", "details" } }}. */
public class ApiErrorEnvelope {

    public ApiErrorBody error;

    public static class ApiErrorBody {
        public String code;
        public String message;
        public com.google.gson.JsonObject details;
    }
}
