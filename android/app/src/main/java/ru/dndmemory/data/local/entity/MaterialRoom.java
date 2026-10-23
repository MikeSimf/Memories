package ru.dndmemory.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Фото-материал кампании в локальной базе: карта, портрет, письмо.
 * Хранится отдельно от объектов справочника: у материалов нет класса,
 * ссылок и котомки — только фото, название и описание.
 */
@Entity(
        tableName = "materials",
        indices = {@Index(value = {"campaignId"})}
)
public class MaterialRoom {

    @PrimaryKey
    @NonNull
    public String id = "";

    public String campaignId;

    public String name;
    public String description;

    /** Файл картинки на сервере: {@code GET /api/files/{id}}. */
    public String imageFileId;

    /** Аватар: одна картинка, отдельно от галереи files. */
    public String avatarFileId;

    /** Файлы материала в JSON: картинки и документы. */
    public String filesJson;

    /** Ссылки из описания: [{targetId, label, resolved}]. */
    public String linksJson;

    public long revision;
    public String createdAt;
    public String updatedAt;
    public boolean deleted;

    /** Есть неотправленная правка — показываем пометку в списке. */
    public boolean dirty;

    /** Состояние сервера при расхождении (json), {@code null} — расхождения нет. */
    public String conflictJson;

    public boolean hasConflict() {
        return conflictJson != null && !conflictJson.isEmpty();
    }
}
