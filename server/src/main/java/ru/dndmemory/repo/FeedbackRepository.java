package ru.dndmemory.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.dndmemory.domain.Feedback;

import java.util.List;
import java.util.UUID;

public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {
    List<Feedback> findAllByOrderByCreatedAtDesc();
}
