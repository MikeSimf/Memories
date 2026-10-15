package ru.dndmemory.data.model;

import androidx.annotation.Nullable;

/** Состояние участника кампании. */
public enum MemberStatus {
    INVITED,
    ACTIVE,
    REMOVED;

    @Nullable
    public static MemberStatus from(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        for (MemberStatus s : values()) {
            if (s.name().equalsIgnoreCase(raw.trim())) {
                return s;
            }
        }
        return null;
    }
}
