package ru.dndmemory.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.dndmemory.domain.EntityType;
import ru.dndmemory.domain.GameEntity;
import ru.dndmemory.domain.Member;
import ru.dndmemory.domain.MemberRole;
import ru.dndmemory.domain.MemberStatus;
import ru.dndmemory.repo.MemberRepository;
import ru.dndmemory.web.ApiException;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Единая точка проверки прав участника кампании (матрица — docs/roles.md). */
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final MemberRepository memberRepository;

    public Member requireMember(UUID campaignId, UUID userId) {
        return memberRepository.findByCampaignIdAndUserId(campaignId, userId)
                .filter(m -> m.getStatus() != MemberStatus.REMOVED)
                .orElseThrow(() -> ApiException.forbidden("Вы не участник этой кампании"));
    }

    public Set<String> permissionSet(Member member) {
        Set<String> permissions = new LinkedHashSet<>();
        switch (member.getRole()) {
            case OWNER:
                permissions.add(Permissions.CAMPAIGN_MANAGE);
                permissions.add(Permissions.MEMBERS_MANAGE);
                permissions.add(Permissions.INVITE_CREATE);
                // fall through
            case EDITOR_MASTER:
                permissions.add(Permissions.ENTITY_READ);
                permissions.add(Permissions.ENTITY_WRITE);
                permissions.add(Permissions.ENTITY_WRITE_OWN);
                permissions.add(Permissions.HERO_WRITE);
                permissions.add(Permissions.RETELLING_READ);
                permissions.add(Permissions.RETELLING_WRITE);
                permissions.add(Permissions.RETELLING_WRITE_OWN);
                permissions.add(Permissions.RETELLING_PUBLISH);
                break;
            case EDITOR_HERO:
                permissions.add(Permissions.ENTITY_READ);
                permissions.add(Permissions.ENTITY_WRITE);
                permissions.add(Permissions.ENTITY_WRITE_OWN);
                permissions.add(Permissions.RETELLING_READ);
                permissions.add(Permissions.RETELLING_WRITE);
                permissions.add(Permissions.RETELLING_WRITE_OWN);
                break;
            case VIEWER:
            default:
                permissions.add(Permissions.ENTITY_READ);
                permissions.add(Permissions.RETELLING_READ);
                break;
        }
        return permissions;
    }

    public void require(Member member, String permission) {
        if (!permissionSet(member).contains(permission)) {
            throw ApiException.forbidden("Недостаточно прав: " + permission);
        }
    }

    /**
     * Может ли участник править этот объект.
     * Героев партии ведёт мастер; игрок с ролью EDITOR_HERO правит только своего героя.
     */
    public boolean canWriteEntity(Member member, UUID entityId, EntityType type) {
        if (member.getRole() == MemberRole.OWNER || member.getRole() == MemberRole.EDITOR_MASTER) {
            return true;
        }
        if (member.getRole() != MemberRole.EDITOR_HERO) {
            return false;
        }
        if (type == EntityType.HERO) {
            return entityId != null && entityId.equals(member.getLinkedEntityId());
        }
        return true;
    }

    public void requireEntityWrite(Member member, GameEntity entity) {
        if (!canWriteEntity(member, entity.getId(), entity.getType())) {
            throw ApiException.forbidden(entity.getType() == EntityType.HERO
                    ? "Героев партии ведёт мастер; вы можете править только своего героя"
                    : "Недостаточно прав для правки объекта");
        }
    }

    public void requireEntityCreate(Member member, EntityType type) {
        if (member.getRole() == MemberRole.VIEWER) {
            throw ApiException.forbidden("Режим просмотра: создание недоступно");
        }
        if (type == EntityType.HERO && member.getRole() == MemberRole.EDITOR_HERO) {
            throw ApiException.forbidden("Создавать героев партии может мастер кампании");
        }
    }
}
