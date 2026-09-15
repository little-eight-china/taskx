package io.taskx.core.domain;

public enum ExecutionStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED;

    public boolean canTransitionTo(ExecutionStatus next) {
        return switch (this) {
            case PENDING -> next == RUNNING || next == FAILED || next == CANCELLED;
            case RUNNING -> next == SUCCESS || next == FAILED || next == CANCELLED || next == PENDING;
            case FAILED -> next == PENDING;
            case SUCCESS, CANCELLED -> false;
        };
    }
}
