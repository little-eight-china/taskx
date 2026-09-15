package io.taskx.core.store;

public final class SlotOccupiedException extends RuntimeException {

    public SlotOccupiedException(int slotNo, String ownerId) {
        super("slot " + slotNo + " is owned by " + ownerId);
    }
}
