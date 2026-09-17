package io.taskx.workflow.runtime;

public enum NodeActivationStatus {
    READY,
    RUNNING,
    RETRY_WAIT,
    WAITING,
    SUCCESS,
    FAILED,
    SKIPPED,
    CANCELLED
}
