package ru.dndmemory.web;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.dndmemory.config.AppProperties;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SystemController {

    private final AppProperties properties;
    private final Environment environment;

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("version", properties.getVersion());
        body.put("time", Instant.now());
        return body;
    }

    @GetMapping("/version")
    public Map<String, Object> version() {
        String[] profiles = environment.getActiveProfiles();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", properties.getVersion());
        body.put("buildTime", Instant.now());
        body.put("profile", profiles.length > 0 ? String.join(",", profiles) : "default");
        body.put("java", System.getProperty("java.version"));
        body.put("profiles", Arrays.asList(profiles));
        return body;
    }
}
