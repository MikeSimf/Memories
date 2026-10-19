package ru.dndmemory.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.annotation.NonNull;

import ru.dndmemory.data.local.dao.CampaignDao;
import ru.dndmemory.data.local.dao.EntityDao;
import ru.dndmemory.data.local.dao.MaterialDao;
import ru.dndmemory.data.local.dao.MemberDao;
import ru.dndmemory.data.local.dao.PendingChangeDao;
import ru.dndmemory.data.local.dao.RetellingDao;
import ru.dndmemory.data.local.dao.RetellingDraftDao;
import ru.dndmemory.data.local.dao.SyncStateDao;
import ru.dndmemory.data.local.entity.CampaignRoom;
import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.data.local.entity.MaterialRoom;
import ru.dndmemory.data.local.entity.MemberRoom;
import ru.dndmemory.data.local.entity.PendingChangeRoom;
import ru.dndmemory.data.local.entity.RetellingDraftRoom;
import ru.dndmemory.data.local.entity.RetellingRoom;
import ru.dndmemory.data.local.entity.SyncStateRoom;

/**
 * Локальная база: единственный источник правды для интерфейса.
 * Сеть меняет её только через {@link ru.dndmemory.sync.SyncEngine}.
 */
@Database(
        entities = {
                CampaignRoom.class,
                EntityRoom.class,
                RetellingRoom.class,
                RetellingDraftRoom.class,
                PendingChangeRoom.class,
                SyncStateRoom.class,
                MemberRoom.class,
                MaterialRoom.class
        },
        version = 5,
        exportSchema = false
)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    /** 1 → 2: таблица фото-материалов кампании. */
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `materials` ("
                    + "`id` TEXT NOT NULL PRIMARY KEY, "
                    + "`campaignId` TEXT, "
                    + "`name` TEXT, "
                    + "`description` TEXT, "
                    + "`imageFileId` TEXT, "
                    + "`revision` INTEGER NOT NULL DEFAULT 0, "
                    + "`createdAt` TEXT, "
                    + "`updatedAt` TEXT, "
                    + "`deleted` INTEGER NOT NULL DEFAULT 0, "
                    + "`dirty` INTEGER NOT NULL DEFAULT 0, "
                    + "`conflictJson` TEXT)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_materials_campaignId` ON `materials` (`campaignId`)");
        }
    };

    public static final String NAME = "dnd_memory.db";

    /** 2 → 3: список файлов у объектов и материалов. */
    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `entities` ADD COLUMN `filesJson` TEXT");
            db.execSQL("ALTER TABLE `materials` ADD COLUMN `filesJson` TEXT");
        }
    };

    /** 3 → 4: класс у персонажа. */
    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // klass уже есть в таблице entities (поле героя), отдельная колонка не нужна.
        }
    };

    /** 4 → 5: типы локаций и предметов. */
    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `entities` ADD COLUMN `locationType` TEXT");
            db.execSQL("ALTER TABLE `entities` ADD COLUMN `itemType` TEXT");
        }
    };

    public abstract CampaignDao campaignDao();

    public abstract EntityDao entityDao();

    public abstract RetellingDao retellingDao();

    public abstract RetellingDraftDao retellingDraftDao();

    public abstract PendingChangeDao pendingChangeDao();

    public abstract SyncStateDao syncStateDao();

    public abstract MemberDao memberDao();

    public abstract MaterialDao materialDao();

    public static AppDatabase create(Context context) {
        return Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, NAME)
                // Материалы кампании появились во второй версии: таблица
                // создаётся миграцией, локальные данные не теряются.
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build();
    }
}
