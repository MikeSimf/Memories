package ru.dndmemory.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.ArrayList;
import java.util.List;

import ru.dndmemory.data.model.EntityLink;
import ru.dndmemory.data.model.HeroItem;

/**
 * Объект кампании: герой, персонаж (НПС), локация или предмет.
 *
 * <p>Все четыре класса живут в одной таблице с дискриминатором {@code kind} —
 * так решил клиент (docs/redesign-v2.md, раздел 2 разрешает и один столбец-признак).
 * Поля, которых у класса нет, остаются {@code null}: у героя — {@code race},
 * {@code klass}, {@code playerUserId} и {@code items}; у персонажа — {@code race}
 * и {@code shortDescription}; у локации своих полей нет; у предмета —
 * {@code ownerName} и одна из ссылок {@code ownerHeroId}/{@code ownerCharacterId}.
 *
 * <p>Пришедший с сервера объект всегда одного класса, поэтому «лишние» поля
 * локальной записи не заполняются и в push не уходят — тело правки собирается
 * по классу в {@code Mappers}.
 */
@Entity(
        tableName = "entities",
        indices = {
                @Index(value = {"campaignId"}),
                @Index(value = {"campaignId", "kind"}),
                @Index(value = {"campaignId", "name"})
        }
)
public class EntityRoom {

    @PrimaryKey
    @NonNull
    public String id = "";

    public String campaignId;

    /** HERO | CHARACTER | LOCATION | ITEM */
    public String kind;

    public String name;
    public String description;

    // --- герой ---------------------------------------------------------------

    public String race;
    public String klass;

    /** Кто отыгрывает героя — ссылка на пользователя, необязательна. */
    public String playerUserId;
    public String playerName;

    /** Котомка героя: {@code hero_items} приходит внутри DTO героя целиком. */
    public List<HeroItem> items = new ArrayList<>();

    // --- персонаж (НПС) ------------------------------------------------------

    public String shortDescription;

    // --- предмет -------------------------------------------------------------

    /** Владелец «как написано в мире». */
    public String ownerName;

    /** Тип локации из справочника location_types. */
    public String locationType;

    /** Тип предмета из справочника item_types. */
    public String itemType;
    public String ownerHeroId;
    public String ownerHeroName;
    public String ownerCharacterId;
    public String ownerCharacterName;

    // --- общее ---------------------------------------------------------------

    public String imageFileId;

    /** Файлы объекта в JSON: фото галереи, карты, документы. */
    public String filesJson;

    public List<EntityLink> links = new ArrayList<>();

    public String createdBy;
    public String updatedBy;
    public long revision;
    public String createdAt;
    public String updatedAt;

    /** Надгробие: объект удалён, но запись нужна для синхронизации. */
    public boolean deleted;

    /** Есть неотправленные локальные правки. */
    public boolean dirty;

    /** JSON расхождения с сервером ({@link ru.dndmemory.data.model.ConflictInfo}). */
    public String conflictJson;

    public EntityRoom() {
    }

    public boolean hasConflict() {
        return conflictJson != null && !conflictJson.isEmpty();
    }
}
