package ru.dndmemory.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.dndmemory.repo.RaceRepository;
import ru.dndmemory.web.dto.RaceDtos;

import java.util.List;
import java.util.stream.Collectors;

/** Справочник рас: нужен редактору героя и персонажа. */
@RestController
@RequestMapping("/api/races")
@RequiredArgsConstructor
public class RaceController {

    private final RaceRepository races;

    @GetMapping
    public List<RaceDtos.RaceDto> list() {
        return races.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(race -> RaceDtos.RaceDto.builder()
                        .code(race.getCode())
                        .name(race.getName())
                        .build())
                .collect(Collectors.toList());
    }
}
