package ru.dndmemory.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.dndmemory.domain.Race;

import java.util.List;

public interface RaceRepository extends JpaRepository<Race, String> {

    List<Race> findAllByOrderBySortOrderAscNameAsc();
}
