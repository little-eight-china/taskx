package io.taskx.core.store;

import java.util.List;
import java.util.OptionalLong;

/**
 * Redis trigger ZSET: {@code trigger:slot:{n}}, member = taskId, score = Unix seconds.
 */
public interface TriggerIndex {

    void add(int slotNo, String taskId, long scoreEpochSecond);

    void remove(int slotNo, String taskId);

    List<DueMember> rangeDue(int slotNo, long nowEpochSecond, int limit);

    default void replace(int slotNo, String taskId, OptionalLong nextScore) {
        if (nextScore.isPresent()) {
            add(slotNo, taskId, nextScore.getAsLong());
        } else {
            remove(slotNo, taskId);
        }
    }

    void clear(int slotNo);
}
