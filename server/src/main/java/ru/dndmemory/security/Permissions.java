package ru.dndmemory.security;

/** Строковые имена прав, которые сервер отдаёт клиентам в myPermissions. */
public final class Permissions {

    public static final String CAMPAIGN_MANAGE = "campaign.manage";
    /** Удаление кампании — только владелец. */
    public static final String CAMPAIGN_DELETE = "campaign.delete";
    public static final String MEMBERS_MANAGE = "members.manage";
    public static final String INVITE_CREATE = "invite.create";
    /** Чтение объектов всех четырёх классов — доступно и гостю. */
    public static final String ENTITY_READ = "entity.read";
    /** Правка персонажей, локаций и предметов. */
    public static final String ENTITY_WRITE = "entity.write";
    /** Героев партии ведёт только владелец кампании. */
    public static final String HERO_WRITE = "hero.write";
    public static final String RETELLING_READ = "retelling.read";
    public static final String RETELLING_WRITE = "retelling.write";
    public static final String RETELLING_WRITE_OWN = "retelling.write.own";
    public static final String RETELLING_PUBLISH = "retelling.publish";

    private Permissions() {
    }
}
