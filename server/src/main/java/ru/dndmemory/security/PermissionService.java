package ru.dndmemory.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.dndmemory.domain.Member;
import ru.dndmemory.domain.MemberRole;
import ru.dndmemory.domain.MemberStatus;
import ru.dndmemory.repo.MemberRepository;
import ru.dndmemory.web.ApiException;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Единая точка проверки прав участника кампании (матрица — docs/roles.md).
 *
 * <p>Героев партии создаёт, правит и удаляет только владелец; персонажей, локации и
 * предметы — владелец, редактор-мастер и редактор-герой; читают все, включая гостя.</p>
 */
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
                permissions.add(Permissions.HERO_WRITE);
                // fall through
            case EDITOR_MASTER:
                permissions.add(Permissions.ENTITY_READ);
                permissions.add(Permissions.ENTITY_WRITE);
                permissions.add(Permissions.RETELLING_READ);
                permissions.add(Permissions.RETELLING_WRITE);
                permissions.add(Permissions.RETELLING_WRITE_OWN);
                permissions.add(Permissions.RETELLING_PUBLISH);
                break;
            case EDITOR_HERO:
                permissions.add(Permissions.ENTITY_READ);
                permissions.add(Permissions.ENTITY_WRITE);
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

    /** Может ли участник править объект этого класса. */
    public boolean canWriteClass(Member member, boolean hero) {
        if (!member.canEditObjects()) {
            return false;
        }
        return !hero || member.getRole() == MemberRole.OWNER;
    }
}
