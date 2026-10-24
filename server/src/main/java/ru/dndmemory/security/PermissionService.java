package ru.dndmemory.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.dndmemory.domain.Member;
import ru.dndmemory.domain.MemberRole;
import ru.dndmemory.domain.MemberStatus;
import ru.dndmemory.repo.MemberRepository;
import ru.dndmemory.repo.UserRepository;
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
    private final UserRepository userRepository;

    public Member requireMember(UUID campaignId, UUID userId) {
        Member member = memberRepository.findByCampaignIdAndUserId(campaignId, userId)
                .filter(m -> m.getStatus() != MemberStatus.REMOVED)
                .orElse(null);
        if (member != null) {
            return member;
        }
        // Суперпользователь system работает как владелец любой кампании, но
        // строки участника у него нет — поэтому собираем её на время запроса.
        if (userRepository.findById(userId).map(u -> u.isSuperuser()).orElse(false)) {
            Member synthetic = new Member();
            synthetic.setId(new UUID(0L, 0L));
            synthetic.setCampaignId(campaignId);
            synthetic.setUserId(userId);
            synthetic.setRole(MemberRole.OWNER);
            synthetic.setStatus(MemberStatus.ACTIVE);
            return synthetic;
        }
        throw ApiException.forbidden("Вы не участник этой кампании");
    }

    public Set<String> permissionSet(Member member) {
        Set<String> permissions = new LinkedHashSet<>();
        switch (member.getRole()) {
            case OWNER:
                permissions.add(Permissions.CAMPAIGN_MANAGE);
                permissions.add(Permissions.CAMPAIGN_DELETE);
                permissions.add(Permissions.MEMBERS_MANAGE);
                permissions.add(Permissions.INVITE_CREATE);
                permissions.add(Permissions.HERO_WRITE);
                // fall through
            case EDITOR_MASTER:
                // Мастер ведёт кампанию вместе с владельцем, но удалять её не может.
                permissions.add(Permissions.CAMPAIGN_MANAGE);
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
