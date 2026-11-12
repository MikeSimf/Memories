package ru.dndmemory.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.dndmemory.domain.CardTemplate;

import java.util.List;
import java.util.UUID;

public interface CardTemplateRepository extends JpaRepository<CardTemplate, UUID> {
    List<CardTemplate> findAllByOrderByObjectTypeAscPriorityDesc();
    List<CardTemplate> findAllByObjectTypeAndKindOrderByPriorityDesc(String objectType, String kind);
}
