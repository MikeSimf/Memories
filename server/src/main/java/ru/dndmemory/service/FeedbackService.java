package ru.dndmemory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dndmemory.config.AppProperties;
import ru.dndmemory.domain.Feedback;
import ru.dndmemory.domain.User;
import ru.dndmemory.repo.FeedbackRepository;
import ru.dndmemory.repo.UserRepository;
import ru.dndmemory.web.ApiException;
import ru.dndmemory.web.dto.FeedbackDtos;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Обратная связь: сохраняем в базу, читает только админ. */
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final AppProperties properties;

    @Transactional
    public FeedbackDtos.FeedbackDto create(UUID userId, FeedbackDtos.CreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Пользователь не найден"));
        Feedback feedback = new Feedback();
        feedback.setId(UUID.randomUUID());
        feedback.setUserId(userId);
        feedback.setEmail(user.getEmail() == null ? "" : user.getEmail());
        feedback.setMessage(request.getMessage().trim());
        feedback.setPage(request.getPage());
        feedback.setStatus("NEW");
        feedback.setCreatedAt(Instant.now());
        feedbackRepository.save(feedback);
        return toDto(feedback);
    }

    @Transactional(readOnly = true)
    public List<FeedbackDtos.FeedbackDto> list(UUID userId) {
        if (!isAdmin(userId)) {
            throw ApiException.forbidden("Нет доступа");
        }
        List<FeedbackDtos.FeedbackDto> result = new ArrayList<>();
        for (Feedback feedback : feedbackRepository.findAllByOrderByCreatedAtDesc()) {
            result.add(toDto(feedback));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public boolean isAdmin(UUID userId) {
        String admin = properties.getAdminEmail();
        if (admin == null || admin.trim().isEmpty()) {
            return false;
        }
        return userRepository.findById(userId)
                .map(user -> admin.trim().equalsIgnoreCase(user.getEmail()))
                .orElse(false);
    }

    private FeedbackDtos.FeedbackDto toDto(Feedback feedback) {
        return FeedbackDtos.FeedbackDto.builder()
                .id(feedback.getId())
                .userId(feedback.getUserId())
                .email(feedback.getEmail())
                .message(feedback.getMessage())
                .page(feedback.getPage())
                .status(feedback.getStatus())
                .createdAt(feedback.getCreatedAt())
                .build();
    }
}
