package ru.dndmemory.web.dto;

import lombok.Builder;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Обратная связь: сообщение пользователя и ответ для админа. */
public final class FeedbackDtos {

    private FeedbackDtos() {
    }

    @Data
    public static class CreateRequest {
        @NotBlank(message = "Напишите сообщение")
        @Size(max = 4000, message = "Не длиннее 4000 символов")
        private String message;
        @Size(max = 200)
        private String page;
    }

    @Data
    @Builder
    public static class FeedbackDto {
        private UUID id;
        private UUID userId;
        private String email;
        private String message;
        private String page;
        private String status;
        private Instant createdAt;
    }
}
