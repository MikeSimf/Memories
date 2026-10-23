package ru.dndmemory.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.dndmemory.security.UserPrincipal;
import ru.dndmemory.service.FeedbackService;
import ru.dndmemory.web.dto.FeedbackDtos;

import javax.validation.Valid;
import java.util.List;

/** Обратная связь: отправка — любому авторизованному, чтение — админу. */
@RestController
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping("/api/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    public FeedbackDtos.FeedbackDto create(@AuthenticationPrincipal UserPrincipal principal,
                                           @Valid @RequestBody FeedbackDtos.CreateRequest request) {
        return feedbackService.create(principal.getId(), request);
    }

    @GetMapping("/api/admin/feedback")
    public List<FeedbackDtos.FeedbackDto> list(@AuthenticationPrincipal UserPrincipal principal) {
        return feedbackService.list(principal.getId());
    }
}
