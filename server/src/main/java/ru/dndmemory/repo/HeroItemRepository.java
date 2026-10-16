package ru.dndmemory.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.dndmemory.domain.HeroItem;

import java.util.List;
import java.util.UUID;

public interface HeroItemRepository extends JpaRepository<HeroItem, UUID> {

    List<HeroItem> findAllByHeroIdOrderByPositionAsc(UUID heroId);

    List<HeroItem> findAllByHeroIdIn(List<UUID> heroIds);

    void deleteAllByHeroId(UUID heroId);
}
