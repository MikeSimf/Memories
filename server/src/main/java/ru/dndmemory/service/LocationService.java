package ru.dndmemory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dndmemory.domain.Location;
import ru.dndmemory.domain.ObjectType;
import ru.dndmemory.repo.CampaignRepository;
import ru.dndmemory.repo.GameEntityRepository;
import ru.dndmemory.repo.LocationRepository;
import ru.dndmemory.repo.MemberRepository;
import ru.dndmemory.security.PermissionService;
import ru.dndmemory.web.dto.LocationDtos;

import java.util.UUID;

/** Локация: своих полей нет, всё нужное уже есть в имени, описании и картинке. */
@Service
public class LocationService extends AbstractObjectService<Location, LocationDtos.LocationDto> {

    private final LocationRepository locationRepository;
    private final CampaignDictionaryService dictionaryService;
    private final CardTemplateService cardTemplateService;

    public LocationService(LocationRepository locationRepository,
                           CampaignRepository campaignRepository,
                           MemberRepository memberRepository,
                           PermissionService permissionService,
                           ChangeRecorder changeRecorder,
                           ObjectIndexService objectIndexService,
                           ConflictRecorder conflictRecorder,
                           ObjectMapper objectMapper,
                       CampaignDictionaryService dictionaryService,
                       CardTemplateService cardTemplateService) {
        super(campaignRepository, memberRepository, permissionService, changeRecorder,
                objectIndexService, conflictRecorder, objectMapper);
        this.locationRepository = locationRepository;
        this.dictionaryService = dictionaryService;
        this.cardTemplateService = cardTemplateService;
    }

    @Override
    public ObjectType objectType() {
        return ObjectType.LOCATION;
    }

    @Override
    protected GameEntityRepository<Location> repository() {
        return locationRepository;
    }

    @Override
    protected Location newEntity() {
        return new Location();
    }

    @Override
    protected String entityLabel() {
        return "Локация";
    }

    @Transactional
    public LocationDtos.LocationDto create(UUID campaignId, UUID userId, LocationDtos.CreateLocationRequest request) {
        return create(campaignId, userId, nonNull(request), request.getId());
    }

    @Transactional
    public LocationDtos.LocationDto update(UUID campaignId, UUID locationId, UUID userId,
                                           LocationDtos.UpdateLocationRequest request, Long ifMatchRevision) {
        return update(campaignId, locationId, userId, nonNull(request), ifMatchRevision);
    }

    @Override
    protected LocationDtos.LocationDto toDto(Location location, ObjectIndexService.ObjectContext context) {
        return LocationDtos.LocationDto.builder()
                .id(location.getId())
                .campaignId(location.getCampaignId())
                .name(location.getName())
                .description(location.getDescription())
                .files(filesOf(location))
                .imageFileId(location.getImageFileId())
                .avatarFileId(location.getAvatarFileId())
                .locationType(location.getLocationType())
                .cardTemplateId(cardTemplate("LOCATION", location))
                .links(objectIndexService.links(context, location.getDescription()))
                .createdBy(location.getCreatedBy())
                .createdByName(context.userName(location.getCreatedBy()))
                .updatedBy(location.getUpdatedBy())
                .updatedByName(context.userName(location.getUpdatedBy()))
                .revision(location.getRevision())
                .createdAt(location.getCreatedAt())
                .updatedAt(location.getUpdatedAt())
                .deleted(location.isDeleted())
                .build();
    }

    private java.util.UUID cardTemplate(String type, Location location) {
        java.util.Map<String, Object> fields = new java.util.HashMap<>();
        fields.put("type", type);
        fields.put("name", location.getName());
        fields.put("locationType", location.getLocationType());
        ru.dndmemory.domain.CardTemplate template = cardTemplateService.match(type, "LIST", fields);
        return template == null ? null : template.getId();
    }

    @Override
    protected void applyPayload(Location location, JsonNode payload) {
        applyFiles(location, payload);
        if (payload == null) {
            return;
        }
        if (payload.hasNonNull("name")) {
            location.setName(payload.get("name").asText());
        }
        if (payload.has("locationType")) {
            location.setLocationType(text(payload.get("locationType")));
            if (location.getLocationType() != null
                    && !dictionaryService.isEnabled(location.getCampaignId(), CampaignDictionaryService.LOCATION_TYPE, location.getLocationType())) {
                location.setLocationType("Неизвестно");
            }
        }
        if (payload.has("description")) {
            location.setDescription(text(payload.get("description")));
        }
        if (payload.has("imageFileId")) {
            location.setImageFileId(uuidOrNull(payload.get("imageFileId")));
        }
        if (payload.has("avatarFileId")) {
            location.setAvatarFileId(uuidOrNull(payload.get("avatarFileId")));
        }
    }
}
