package ru.dndmemory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dndmemory.domain.Character;
import ru.dndmemory.domain.ObjectType;
import ru.dndmemory.repo.CampaignRepository;
import ru.dndmemory.repo.CharacterRepository;
import ru.dndmemory.repo.GameEntityRepository;
import ru.dndmemory.repo.MemberRepository;
import ru.dndmemory.security.PermissionService;
import ru.dndmemory.web.dto.CharacterDtos;

import java.util.Locale;
import java.util.UUID;

/** Персонаж (НПС): поиск идёт ещё и по расе с коротким описанием. */
@Service
public class CharacterService extends AbstractObjectService<Character, CharacterDtos.CharacterDto> {

    private final CharacterRepository characterRepository;

    public CharacterService(CharacterRepository characterRepository,
                            CampaignRepository campaignRepository,
                            MemberRepository memberRepository,
                            PermissionService permissionService,
                            ChangeRecorder changeRecorder,
                            ObjectIndexService objectIndexService,
                            ConflictRecorder conflictRecorder,
                            ObjectMapper objectMapper) {
        super(campaignRepository, memberRepository, permissionService, changeRecorder,
                objectIndexService, conflictRecorder, objectMapper);
        this.characterRepository = characterRepository;
    }

    @Override
    public ObjectType objectType() {
        return ObjectType.CHARACTER;
    }

    @Override
    protected GameEntityRepository<Character> repository() {
        return characterRepository;
    }

    @Override
    protected Character newEntity() {
        return new Character();
    }

    @Override
    protected String entityLabel() {
        return "Персонаж";
    }

    @Transactional
    public CharacterDtos.CharacterDto create(UUID campaignId, UUID userId, CharacterDtos.CreateCharacterRequest request) {
        return create(campaignId, userId, nonNull(request), request.getId());
    }

    @Transactional
    public CharacterDtos.CharacterDto update(UUID campaignId, UUID characterId, UUID userId,
                                             CharacterDtos.UpdateCharacterRequest request, Long ifMatchRevision) {
        return update(campaignId, characterId, userId, nonNull(request), ifMatchRevision);
    }

    @Override
    protected boolean matchesQuery(Character entity, String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        return contains(entity.getName(), q)
                || contains(entity.getDescription(), q)
                || contains(entity.getRace(), q)
                || contains(entity.getShortDescription(), q);
    }

    @Override
    protected CharacterDtos.CharacterDto toDto(Character character, ObjectIndexService.ObjectContext context) {
        return CharacterDtos.CharacterDto.builder()
                .id(character.getId())
                .campaignId(character.getCampaignId())
                .name(character.getName())
                .description(character.getDescription())
                .files(filesOf(character))
                .imageFileId(character.getImageFileId())
                .links(objectIndexService.links(context, character.getDescription(), character.getShortDescription()))
                .createdBy(character.getCreatedBy())
                .createdByName(context.userName(character.getCreatedBy()))
                .updatedBy(character.getUpdatedBy())
                .updatedByName(context.userName(character.getUpdatedBy()))
                .revision(character.getRevision())
                .createdAt(character.getCreatedAt())
                .updatedAt(character.getUpdatedAt())
                .deleted(character.isDeleted())
                .race(character.getRace())
                .shortDescription(character.getShortDescription())
                .build();
    }

    @Override
    protected void applyPayload(Character character, JsonNode payload) {
        applyFiles(character, payload);
        if (payload == null) {
            return;
        }
        if (payload.hasNonNull("name")) {
            character.setName(payload.get("name").asText());
        }
        if (payload.has("description")) {
            character.setDescription(text(payload.get("description")));
        }
        if (payload.has("imageFileId")) {
            character.setImageFileId(uuidOrNull(payload.get("imageFileId")));
        }
        if (payload.has("race")) {
            character.setRace(text(payload.get("race")));
        }
        if (payload.has("shortDescription")) {
            character.setShortDescription(text(payload.get("shortDescription")));
        }
    }
}
