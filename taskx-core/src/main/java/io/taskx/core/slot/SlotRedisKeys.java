package io.taskx.core.slot;

/**
 * Redis key layout for a slot. Lock implementation lives in {@code taskx-common}.
 */
public final class SlotRedisKeys {

    private SlotRedisKeys() {
    }

    public static String trigger(int slotNo) {
        requireSlot(slotNo);
        return "trigger:slot:" + slotNo;
    }

    public static String lock(int slotNo) {
        requireSlot(slotNo);
        return "lock:slot:" + slotNo;
    }

    private static void requireSlot(int slotNo) {
        if (slotNo < 0) {
            throw new IllegalArgumentException("slotNo must be >= 0");
        }
    }
}
