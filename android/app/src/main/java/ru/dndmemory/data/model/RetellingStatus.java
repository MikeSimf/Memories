package ru.dndmemory.data.model;

import androidx.annotation.Nullable;

/**
 * Состояние пересказа. {@code DRAFT} существует только на устройстве и никогда
 * не попадает в синхронизацию (docs/sync.md, раздел 4).
 */
public enum RetellingStatus {
    DRAFT,
    PUBLISHED;

    @Nullable
    public static RetellingStatus from(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        for (RetellingStatus s : values()) {
            if (s.name().equalsIgnoreCase(raw.trim())) {
                return s;
            }
        }
        return null;
    }
}
