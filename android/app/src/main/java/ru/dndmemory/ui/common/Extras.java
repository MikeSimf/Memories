package ru.dndmemory.ui.common;

/** Ключи extras для навигации между экранами. */
public final class Extras {

    /** Идентификатор фото-материала кампании. */
    public static final String MATERIAL_ID = "ru.dndmemory.extra.MATERIAL_ID";

    public static final String CAMPAIGN_ID = "campaignId";
    /** Какая вкладка кампании открыта: characters, locations, items. */
    public static final String CAMPAIGN_TAB = "campaignTab";
    /** Открыть настройки сразу на профиле. */
    public static final String SETTINGS_PROFILE = "settingsProfile";
    public static final String ENTITY_ID = "entityId";
    public static final String ENTITY_TYPE = "entityType";
    public static final String RETELLING_ID = "retellingId";
    public static final String DRAFT_ID = "draftId";
    public static final String INVITE_TOKEN = "inviteToken";
    public static final String SEARCH_QUERY = "searchQuery";
    public static final String SEARCH_TYPE = "searchType";
    public static final String CREATE_NEW = "createNew";
    public static final String AUTHOR_ENTITY_ID = "authorEntityId";
    /** Вкладка экрана входа: вход или регистрация. */
    public static final String AUTH_TAB = "authTab";
    /** Пояснение к экрану: например причина обрыва сессии. */
    public static final String MESSAGE = "message";

    public static final String AUTH_TAB_LOGIN = "login";
    public static final String AUTH_TAB_REGISTER = "register";

    private Extras() {
    }
}
