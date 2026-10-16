package ru.dndmemory.data.remote.dto;

/**
 * PATCH /api/me — профиль и контакты для восстановления пароля.
 *
 * <p>Пустая строка в {@code phone} очищает значение; {@code email} и {@code phone}
 * нормализует сервер (docs/api.md, раздел 3). Логин не меняется.
 */
public class PatchMeRequest {

    public String displayName;
    public String avatarFileId;
    public String email;
    public String phone;

    public PatchMeRequest(String displayName, String avatarFileId, String email, String phone) {
        this.displayName = displayName;
        this.avatarFileId = avatarFileId;
        this.email = email;
        this.phone = phone;
    }

    /** Только имя и аватар — для мест, где контакты не меняются. */
    public static PatchMeRequest nameOnly(String displayName, String avatarFileId) {
        return new PatchMeRequest(displayName, avatarFileId, null, null);
    }
}
