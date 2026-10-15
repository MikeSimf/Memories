package ru.dndmemory.data.remote;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;

import retrofit2.Response;
import ru.dndmemory.data.remote.dto.ApiErrorEnvelope;

/**
 * Ошибка обращения к серверу: разобранное тело {@code {"error":{...}}},
 * HTTP-статус и {@code X-Request-Id} (docs/api.md, разделы 1 и 12).
 */
public class ApiException extends IOException {

    public static final String CODE_NETWORK = "network";
    public static final String CODE_UNKNOWN = "unknown";
    public static final String CODE_UNAUTHORIZED = "unauthorized";
    public static final String CODE_RESYNC_REQUIRED = "resync_required";
    public static final String CODE_DRAFT_NOT_SYNCED = "draft_not_synced";

    public static final int NO_STATUS = -1;

    private final int httpStatus;
    private final String code;
    private final String serverMessage;
    private final String requestId;
    private final JsonObject detailsJson;

    public ApiException(int httpStatus, String code, String serverMessage,
                        @Nullable String requestId, @Nullable JsonObject detailsJson) {
        super(serverMessage == null ? code : serverMessage);
        this.httpStatus = httpStatus;
        this.code = code == null ? CODE_UNKNOWN : code;
        this.serverMessage = serverMessage;
        this.requestId = requestId;
        this.detailsJson = detailsJson;
    }

    public static ApiException network(IOException cause) {
        ApiException e = new ApiException(NO_STATUS, CODE_NETWORK,
                cause == null ? null : cause.getMessage(), null, null);
        if (cause != null) {
            e.initCause(cause);
        }
        return e;
    }

    public static ApiException unexpected(Throwable cause) {
        ApiException e = new ApiException(NO_STATUS, CODE_UNKNOWN,
                cause == null ? null : cause.getMessage(), null, null);
        if (cause != null) {
            e.initCause(cause);
        }
        return e;
    }

    /** Разбирает {@code response} с ошибкой, включая {@code details}. */
    public static ApiException fromResponse(Response<?> response) {
        int status = response.code();
        String requestId = response.headers().get("X-Request-Id");
        String code = null;
        String message = null;
        JsonObject details = null;
        try {
            if (response.errorBody() != null) {
                String raw = response.errorBody().string();
                if (raw != null && !raw.trim().isEmpty()) {
                    ApiErrorEnvelope envelope =
                            new Gson().fromJson(raw, ApiErrorEnvelope.class);
                    if (envelope != null && envelope.error != null) {
                        code = envelope.error.code;
                        message = envelope.error.message;
                        details = envelope.error.details;
                    }
                }
            }
        } catch (Exception ignored) {
            // тело ошибки не обязательно разбирается — статуса достаточно
        }
        if (code == null) {
            code = defaultCodeFor(status);
        }
        if (message == null) {
            message = "HTTP " + status;
        }
        return new ApiException(status, code, message, requestId, details);
    }

    private static String defaultCodeFor(int status) {
        switch (status) {
            case 401:
                return CODE_UNAUTHORIZED;
            case 403:
                return "forbidden";
            case 404:
                return "not_found";
            case 409:
                return "conflict";
            case 410:
                return CODE_RESYNC_REQUIRED;
            case 422:
                return "validation_failed";
            case 429:
                return "rate_limited";
            default:
                return status >= 500 ? "internal_error" : CODE_UNKNOWN;
        }
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    @Nullable
    public String getServerMessage() {
        return serverMessage;
    }

    @Nullable
    public String getRequestId() {
        return requestId;
    }

    @Nullable
    public JsonObject getDetails() {
        return detailsJson;
    }

    public boolean isNetwork() {
        return CODE_NETWORK.equals(code);
    }

    public boolean isUnauthorized() {
        return httpStatus == 401 || CODE_UNAUTHORIZED.equals(code);
    }

    public boolean isForbidden() {
        return httpStatus == 403 || "forbidden".equals(code);
    }

    public boolean isNotFound() {
        return httpStatus == 404 || "not_found".equals(code);
    }

    public boolean isConflict() {
        return httpStatus == 409 || "conflict".equals(code);
    }

    public boolean isResyncRequired() {
        return httpStatus == 410 || CODE_RESYNC_REQUIRED.equals(code);
    }

    public boolean isDraftNotSynced() {
        return httpStatus == 422 || CODE_DRAFT_NOT_SYNCED.equals(code);
    }

    public boolean isRateLimited() {
        return httpStatus == 429 || "rate_limited".equals(code);
    }

    /** Состояние сервера из {@code details.server} при конфликте (409). */
    @Nullable
    public JsonObject conflictServer() {
        if (detailsJson == null || !detailsJson.has("server") || detailsJson.get("server").isJsonNull()) {
            return null;
        }
        return detailsJson.getAsJsonObject("server");
    }

    @Override
    public String toString() {
        return "ApiException{" + httpStatus + " " + code
                + (requestId == null ? "" : ", requestId=" + requestId) + '}';
    }
}
