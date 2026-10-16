package ru.dndmemory.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import ru.dndmemory.domain.ImageExtender;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Общие запросы всех четырёх классов справочника. Конкретные репозитории объявляют
 * тип: {@code HeroRepository extends GameEntityRepository<Hero>} — так Spring Data
 * разрешает параметр в листовой класс.
 *
 * <p>Поиска «по ключу сопоставления» здесь нет намеренно: ключ вычисляется методом
 * самого класса, поэтому кандидаты берутся списком кампании.</p>
 */
@NoRepositoryBean
public interface GameEntityRepository<T extends ImageExtender> extends JpaRepository<T, UUID> {

    Optional<T> findByIdAndCampaignId(UUID id, UUID campaignId);

    List<T> findAllByCampaignIdAndDeletedFalseOrderByNameAsc(UUID campaignId);

    List<T> findAllByCampaignIdAndDeletedFalse(UUID campaignId);

    Page<T> findAllByCampaignIdAndDeletedFalse(UUID campaignId, Pageable pageable);

    long countByCampaignIdAndDeletedFalse(UUID campaignId);
}
