package io.taskx.core.store;

/**
 * Could not acquire {@code lock:slot:{n}} (executor holding it, or another admin call).
 */
public final class SlotBusyException extends RuntimeException {

    private final int slotNo;

    public SlotBusyException(int slotNo) {
        super("could not lock slot " + slotNo);
        this.slotNo = slotNo;
    }

    public int slotNo() {
        return slotNo;
    }
}
