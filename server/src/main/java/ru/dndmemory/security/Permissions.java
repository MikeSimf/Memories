package ru.dndmemory.security;

/** Строковые имена прав, которые сервер отдаёт клиентам в myPermissions. */
public final class Permissions {

    public static final String CAMPAIGN_MANAGE = "campaign.manage";
    public static final String MEMBERS_MANAGE = "members.manage";
    public static final String INVITE_CREATE = "invite.create";
    public static final String ENTITY_READ = "entity.read";
    public static final String ENTITY_WRITE = "entity.write";
    public static final String ENTITY_WRITE_OWN = "entity.write.own";
    public static final String HERO_WRITE = "hero.write";
    public static final String RETELLING_READ = "retelling.read";
    public static final String RETELLING_WRITE = "retelling.write";
    public static final String RETELLING_WRITE_OWN = "retelling.write.own";
    public static final String RETELLING_PUBLISH = "retelling.publish";

    private Permissions() {
    }
}
