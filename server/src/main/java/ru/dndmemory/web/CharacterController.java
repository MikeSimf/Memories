package ru.dndmemory.web;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.dndmemory.security.UserPrincipal;
import ru.dndmemory.service.CharacterService;
import ru.dndmemory.web.dto.CharacterDtos;
import ru.dndmemory.web.dto.ObjectListDto;

import javax.validation.Valid;
import java.time.Instant;
import java.util.UUID;

/** Персонажи (НПС): правят владелец, редактор-мастер и редактор-герой. */
@RestController
@RequestMapping("/api/campaigns/{campaignId}/characters")
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterService characterService;

    @GetMapping
    public ObjectListDto<CharacterDtos.CharacterDto> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID campaignId,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant updatedSince,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return characterService.list(campaignId, principal.getId(), query, updatedSince, limit, offset);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CharacterDtos.CharacterDto create(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable UUID campaignId,
                                             @Valid @RequestBody CharacterDtos.CreateCharacterRequest request) {
        return characterService.create(campaignId, principal.getId(), request);
    }

    @GetMapping("/{characterId}")
    public CharacterDtos.CharacterDto get(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable UUID campaignId,
                                          @PathVariable UUID characterId) {
        return characterService.get(campaignId, characterId, principal.getId());
    }

    @PatchMapping("/{characterId}")
    public CharacterDtos.CharacterDto update(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable UUID campaignId,
                                             @PathVariable UUID characterId,
                                             @RequestHeader(value = "If-Match", required = false) Long ifMatchRevision,
                                             @Valid @RequestBody CharacterDtos.UpdateCharacterRequest request) {
        return characterService.update(campaignId, characterId, principal.getId(), request, ifMatchRevision);
    }

    @DeleteMapping("/{characterId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal principal,
                       @PathVariable UUID campaignId,
                       @PathVariable UUID characterId) {
        characterService.delete(campaignId, characterId, principal.getId());
    }
}
