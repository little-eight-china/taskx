package io.taskx.core.poll;

import java.util.OptionalLong;

@FunctionalInterface
public interface TerminalScoreWriter {

    void write(int slotNo, String taskId, OptionalLong nextScore);
}
