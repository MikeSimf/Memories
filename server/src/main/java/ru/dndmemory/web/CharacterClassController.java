package ru.dndmemory.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.dndmemory.repo.CharacterClassRepository;
import ru.dndmemory.web.dto.RaceDtos;

import java.util.List;
import java.util.stream.Collectors;

/** Справочник классов персонажа: формат тот же, что у рас (code — name). */
@RestController
@RequestMapping("/api/classes")
@RequiredArgsConstructor
public class CharacterClassController {

    private final CharacterClassRepository classes;

    @GetMapping
    public List<RaceDtos.RaceDto> list() {
        return classes.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(item -> RaceDtos.RaceDto.builder()
                        .code(item.getCode())
                        .name(item.getName())
                        .build())
                .collect(Collectors.toList());
    }
}
