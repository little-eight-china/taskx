package io.taskx.core.domain;

import io.taskx.core.Require;

public record SlotOwnership(int slotNo, String executorId) {

    public SlotOwnership {
        if (slotNo < 0) {
            throw new IllegalArgumentException("slotNo must be >= 0");
        }
        executorId = Require.notBlank(executorId, "executorId");
    }
}
