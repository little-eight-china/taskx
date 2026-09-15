package io.taskx.core.slot;

public record SlotConfig(int slotCount) {

    public static final int DEFAULT_SLOT_COUNT = 32;

    public static final SlotConfig DEFAULT = new SlotConfig(DEFAULT_SLOT_COUNT);

    public SlotConfig {
        if (slotCount <= 0) {
            throw new IllegalArgumentException("slotCount must be > 0");
        }
    }

    public int slotOf(String taskId) {
        return SlotHasher.slotOf(taskId, slotCount);
    }
}
