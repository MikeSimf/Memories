package ru.dndmemory.data.model;

import androidx.annotation.Nullable;

/** Операция изменения в журнале синхронизации. */
public enum ChangeOp {
    UPSERT,
    DELETE;

    @Nullable
    public static ChangeOp from(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        for (ChangeOp op : values()) {
            if (op.name().equalsIgnoreCase(raw.trim())) {
                return op;
            }
        }
        return null;
    }
}
