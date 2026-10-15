package ru.dndmemory.web;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/** Ошибка API с кодом из docs/api.md. */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> details;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? new LinkedHashMap<>() : details;
    }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, "not_found", what + " не найден");
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "forbidden", message);
    }

    public static ApiException validation(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "validation_failed", message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, "conflict", message);
    }

    public static ApiException emailTaken() {
        return new ApiException(HttpStatus.CONFLICT, "email_taken", "Пользователь с такой почтой уже существует");
    }

    public static ApiException inviteInvalid() {
        return new ApiException(HttpStatus.CONFLICT, "invite_invalid", "Приглашение недействительно или просрочено");
    }

    public static ApiException draftNotSynced() {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "draft_not_synced",
                "Черновик пересказа хранится на устройстве и на сервер не отправляется");
    }

    public static ApiException resyncRequired() {
        return new ApiException(HttpStatus.GONE, "resync_required",
                "Журнал изменений компактизован — требуется полная перезагрузка кампании");
    }
}
