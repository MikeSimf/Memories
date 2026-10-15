package ru.dndmemory.data.model;

import androidx.annotation.Nullable;

/** Роль участника кампании (docs/roles.md). */
public enum MemberRole {
    OWNER,
    EDITOR_MASTER,
    EDITOR_HERO,
    VIEWER;

    @Nullable
    public static MemberRole from(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        for (MemberRole r : values()) {
            if (r.name().equalsIgnoreCase(raw.trim())) {
                return r;
            }
        }
        return null;
    }

    public boolean canManageCampaign() {
        return this == OWNER;
    }

    public boolean canManageMembers() {
        return this == OWNER;
    }

    public boolean canWriteEntities() {
        return this == OWNER || this == EDITOR_MASTER || this == EDITOR_HERO;
    }

    public boolean canPublishRetellings() {
        return this == OWNER || this == EDITOR_MASTER;
    }
}
