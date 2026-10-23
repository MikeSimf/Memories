package ru.dndmemory.data.remote.dto;

/** POST /api/feedback — сообщение обратной связи. */
public class FeedbackRequest {

    public String message;
    public String page;

    public FeedbackRequest(String message, String page) {
        this.message = message;
        this.page = page;
    }
}
