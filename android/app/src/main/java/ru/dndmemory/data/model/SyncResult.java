package ru.dndmemory.data.model;

import androidx.annotation.Nullable;

/** Итог одного цикла синхронизации кампании. */
public class SyncResult {

    public boolean success;
    public boolean resyncRequired;
    public int pushed;
    public int applied;
    public int conflictResolved;
    public int rejected;
    public int pulled;
    public long latestRevision;
    @Nullable
    public String errorMessage;

    public static SyncResult ok(int applied, int conflictResolved, int pulled, long latestRevision) {
        SyncResult r = new SyncResult();
        r.success = true;
        r.applied = applied;
        r.conflictResolved = conflictResolved;
        r.pushed = applied + conflictResolved;
        r.pulled = pulled;
        r.latestRevision = latestRevision;
        return r;
    }

    public static SyncResult failure(@Nullable String message) {
        SyncResult r = new SyncResult();
        r.success = false;
        r.errorMessage = message;
        return r;
    }

    public static SyncResult resync() {
        SyncResult r = new SyncResult();
        r.success = false;
        r.resyncRequired = true;
        return r;
    }

    public boolean hasConflicts() {
        return rejected > 0;
    }
}
