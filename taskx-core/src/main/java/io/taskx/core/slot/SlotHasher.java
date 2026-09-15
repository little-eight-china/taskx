package io.taskx.core.slot;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * Maps {@code taskId} to a slot. Algorithm: CRC-32/IEEE of UTF-8 bytes, unsigned 32-bit modulo N.
 */
public final class SlotHasher {

    private SlotHasher() {
    }

    public static int slotOf(String taskId, int slotCount) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId must not be blank");
        }
        if (slotCount <= 0) {
            throw new IllegalArgumentException("slotCount must be > 0");
        }
        CRC32 crc = new CRC32();
        crc.update(taskId.getBytes(StandardCharsets.UTF_8));
        return (int) (crc.getValue() % slotCount);
    }
}
