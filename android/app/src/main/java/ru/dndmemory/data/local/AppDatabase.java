package ru.dndmemory.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import ru.dndmemory.data.local.dao.CampaignDao;
import ru.dndmemory.data.local.dao.EntityDao;
import ru.dndmemory.data.local.dao.MemberDao;
import ru.dndmemory.data.local.dao.PendingChangeDao;
import ru.dndmemory.data.local.dao.RetellingDao;
import ru.dndmemory.data.local.dao.RetellingDraftDao;
import ru.dndmemory.data.local.dao.SyncStateDao;
import ru.dndmemory.data.local.entity.CampaignRoom;
import ru.dndmemory.data.local.entity.EntityRoom;
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
                MemberRoom.class
        },
        version = 1,
        exportSchema = false
)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    public static final String NAME = "dnd_memory.db";

    public abstract CampaignDao campaignDao();

    public abstract EntityDao entityDao();

    public abstract RetellingDao retellingDao();

    public abstract RetellingDraftDao retellingDraftDao();

    public abstract PendingChangeDao pendingChangeDao();

    public abstract SyncStateDao syncStateDao();

    public abstract MemberDao memberDao();

    public static AppDatabase create(Context context) {
        return Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, NAME)
                .fallbackToDestructiveMigration()
                .build();
    }
}
