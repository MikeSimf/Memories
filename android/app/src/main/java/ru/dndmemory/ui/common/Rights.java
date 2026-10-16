package ru.dndmemory.ui.common;

import androidx.annotation.Nullable;

import ru.dndmemory.data.local.entity.CampaignRoom;
import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.data.local.entity.RetellingRoom;
import ru.dndmemory.data.model.EntityType;
import ru.dndmemory.data.model.MemberRole;

/**
 * Права из docs/roles.md и docs/api.md (раздел 6). Сервер всё равно проверяет
 * их сам — здесь мы только не показываем недоступные действия.
 *
 * <p>Героев ведёт только владелец кампании: право {@code hero.write}.
 * Персонажей, локации и предметы — владелец, редактор-мастер и редактор-герой:
 * право {@code entity.write}.
 */
public final class Rights {

    public static final String CAMPAIGN_MANAGE = "campaign.manage";
    public static final String MEMBERS_MANAGE = "members.manage";
    public static final String INVITE_CREATE = "invite.create";
    public static final String ENTITY_READ = "entity.read";
    public static final String ENTITY_WRITE = "entity.write";
    public static final String HERO_WRITE = "hero.write";
    public static final String RETELLING_READ = "retelling.read";
    public static final String RETELLING_WRITE = "retelling.write";
    public static final String RETELLING_WRITE_OWN = "retelling.write.own";
    public static final String RETELLING_PUBLISH = "retelling.publish";

    private Rights() {
    }

    public static boolean has(@Nullable CampaignRoom campaign, String permission) {
        if (campaign == null) {
            return false;
        }
        if (campaign.hasPermission(permission)) {
            return true;
        }
        // Свежесозданная кампания приходит без myPermissions — владелец имеет всё.
        return campaign.myPermissions != null && campaign.myPermissions.isEmpty()
                && MemberRole.OWNER == MemberRole.from(campaign.myRole);
    }

    public static boolean canReadEntities(@Nullable CampaignRoom campaign) {
        return has(campaign, ENTITY_READ)
                || MemberRole.from(campaign == null ? null : campaign.myRole) != null;
    }

    /** Право на создание, правку и удаление объекта класса {@code kind}. */
    public static boolean canWrite(@Nullable CampaignRoom campaign, @Nullable EntityType kind) {
        if (kind == null) {
            return false;
        }
        if (kind.requiresHeroPermission()) {
            return has(campaign, HERO_WRITE);
        }
        return has(campaign, ENTITY_WRITE);
    }

    /** Хоть что-то создавать на вкладке: либо героя, либо остальные классы. */
    public static boolean canCreateEntity(@Nullable CampaignRoom campaign) {
        return has(campaign, ENTITY_WRITE) || has(campaign, HERO_WRITE);
    }

    public static boolean canWriteEntity(@Nullable CampaignRoom campaign, @Nullable EntityRoom entity,
                                         @Nullable String currentUserId) {
        if (entity == null) {
            return false;
        }
        return canWrite(campaign, EntityType.from(entity.kind));
    }

    public static boolean canDeleteEntity(@Nullable CampaignRoom campaign, @Nullable EntityRoom entity,
                                          @Nullable String currentUserId) {
        return canWriteEntity(campaign, entity, currentUserId);
    }

    public static boolean canCreateRetelling(@Nullable CampaignRoom campaign) {
        MemberRole role = MemberRole.from(campaign == null ? null : campaign.myRole);
        return role != null && role.canWriteEntities();
    }

    public static boolean canEditRetelling(@Nullable CampaignRoom campaign, @Nullable RetellingRoom retelling,
                                           @Nullable String currentUserId) {
        if (campaign == null) {
            return false;
        }
        if (has(campaign, RETELLING_PUBLISH)) {
            return true;
        }
        if (retelling != null && currentUserId != null && currentUserId.equals(retelling.createdBy)) {
            return has(campaign, RETELLING_WRITE_OWN) || has(campaign, RETELLING_WRITE);
        }
        return false;
    }

    public static boolean canDeleteRetelling(@Nullable CampaignRoom campaign, @Nullable RetellingRoom retelling,
                                             @Nullable String currentUserId) {
        if (has(campaign, CAMPAIGN_MANAGE)) {
            return true;
        }
        return retelling != null && currentUserId != null
                && currentUserId.equals(retelling.createdBy);
    }

    public static boolean canManageMembers(@Nullable CampaignRoom campaign) {
        return has(campaign, MEMBERS_MANAGE);
    }

    public static boolean canInvite(@Nullable CampaignRoom campaign) {
        return has(campaign, INVITE_CREATE);
    }

    public static boolean canManageCampaign(@Nullable CampaignRoom campaign) {
        return has(campaign, CAMPAIGN_MANAGE);
    }
}
