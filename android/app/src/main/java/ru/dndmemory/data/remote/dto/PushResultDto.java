package ru.dndmemory.data.remote.dto;

/** Результат применения одной правки. */
public class PushResultDto {

    public String clientChangeId;
    public String objectId;
    public long revision;
    /** APPLIED | CONFLICT_RESOLVED | REJECTED */
    public String status;
    public ConflictDto conflict;

    public boolean isApplied() {
        return "APPLIED".equalsIgnoreCase(status);
    }

    public boolean isConflictResolved() {
        return "CONFLICT_RESOLVED".equalsIgnoreCase(status);
    }

    public boolean isRejected() {
        return "REJECTED".equalsIgnoreCase(status);
    }

    public boolean isAccepted() {
        return isApplied() || isConflictResolved();
    }
}
