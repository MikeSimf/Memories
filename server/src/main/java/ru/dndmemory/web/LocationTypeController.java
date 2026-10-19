package ru.dndmemory.web;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.dndmemory.web.dto.TypeDtos;

import java.util.List;

/** Справочник типов локаций: по типу выбирается картинка по умолчанию. */
@RestController
@RequestMapping("/api/location-types")
@RequiredArgsConstructor
public class LocationTypeController {

    private final JdbcTemplate jdbc;

    @GetMapping
    public List<TypeDtos.TypeDto> list() {
        return jdbc.query(
                "select code, name from location_types order by sort_order, name",
                (rs, rowNum) -> TypeDtos.TypeDto.builder()
                        .code(rs.getString("code"))
                        .name(rs.getString("name"))
                        .build());
    }
}
