package ru.dndmemory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dndmemory.domain.Material;
import ru.dndmemory.domain.ObjectType;
import ru.dndmemory.repo.CampaignRepository;
import ru.dndmemory.repo.GameEntityRepository;
import ru.dndmemory.repo.MaterialRepository;
import ru.dndmemory.repo.MemberRepository;
import ru.dndmemory.security.PermissionService;
import ru.dndmemory.web.dto.MaterialDtos;

import java.util.UUID;

/**
 * Фото-материалы кампании. Правила те же, что у персонажей, локаций и предметов:
 * создаёт и правит владелец, редактор-мастер и редактор-герой, читают все
 * участники (docs/roles.md).
 */
@Service
public class MaterialService extends AbstractObjectService<Material, MaterialDtos.MaterialDto> {

    private final MaterialRepository materialRepository;

    public MaterialService(MaterialRepository materialRepository,
                           CampaignRepository campaignRepository,
                           MemberRepository memberRepository,
                           PermissionService permissionService,
                           ChangeRecorder changeRecorder,
                           ObjectIndexService objectIndexService,
                           ConflictRecorder conflictRecorder,
                           ObjectMapper objectMapper) {
        super(campaignRepository, memberRepository, permissionService, changeRecorder,
                objectIndexService, conflictRecorder, objectMapper);
        this.materialRepository = materialRepository;
    }

    @Override
    public ObjectType objectType() {
        return ObjectType.MATERIAL;
    }

    @Override
    protected GameEntityRepository<Material> repository() {
        return materialRepository;
    }

    @Override
    protected Material newEntity() {
        return new Material();
    }

    @Override
    protected String entityLabel() {
        return "Материал";
    }

    @Transactional
    public MaterialDtos.MaterialDto create(UUID campaignId, UUID userId,
                                           MaterialDtos.CreateMaterialRequest request) {
        return create(campaignId, userId, nonNull(request), request.getId());
    }

    @Transactional
    public MaterialDtos.MaterialDto update(UUID campaignId, UUID materialId, UUID userId,
                                           MaterialDtos.UpdateMaterialRequest request,
                                           Long ifMatchRevision) {
        return update(campaignId, materialId, userId, nonNull(request), ifMatchRevision);
    }

    @Override
    protected MaterialDtos.MaterialDto toDto(Material material, ObjectIndexService.ObjectContext context) {
        return MaterialDtos.MaterialDto.builder()
                .id(material.getId())
                .campaignId(material.getCampaignId())
                .name(material.getName())
                .description(material.getDescription())
                .imageFileId(material.getImageFileId())
                .links(objectIndexService.links(context, material.getDescription()))
                .createdBy(material.getCreatedBy())
                .createdByName(context.userName(material.getCreatedBy()))
                .updatedBy(material.getUpdatedBy())
                .updatedByName(context.userName(material.getUpdatedBy()))
                .revision(material.getRevision())
                .createdAt(material.getCreatedAt())
                .updatedAt(material.getUpdatedAt())
                .deleted(material.isDeleted())
                .build();
    }

    @Override
    protected void applyPayload(Material material, JsonNode payload) {
        if (payload == null) {
            return;
        }
        if (payload.hasNonNull("name")) {
            material.setName(payload.get("name").asText());
        }
        if (payload.has("description")) {
            material.setDescription(text(payload.get("description")));
        }
        if (payload.has("imageFileId")) {
            material.setImageFileId(uuidOrNull(payload.get("imageFileId")));
        }
    }
}
