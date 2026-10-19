package ru.dndmemory.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.dndmemory.domain.CharacterClass;

import java.util.List;

public interface CharacterClassRepository extends JpaRepository<CharacterClass, String> {

    List<CharacterClass> findAllByOrderBySortOrderAscNameAsc();
}
