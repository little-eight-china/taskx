package io.taskx.meta.redis;

import io.taskx.workflow.store.WorkflowReadyIndex;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class RedissonWorkflowReadyIndex implements WorkflowReadyIndex {

    private static final Pattern GROUP = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final RedissonClient redisson;

    public RedissonWorkflowReadyIndex(RedissonClient redisson) {
        this.redisson = Objects.requireNonNull(redisson, "redisson");
    }

    @Override
    public void add(String workerGroup, int slotNo, String activationId, Instant availableAt) {
        zset(workerGroup, slotNo).add(availableAt.getEpochSecond(), activationId);
    }

    @Override
    public void remove(String workerGroup, int slotNo, String activationId) {
        zset(workerGroup, slotNo).remove(activationId);
    }

    @Override
    public List<String> rangeDue(String workerGroup, int slotNo, Instant now, int limit) {
        Collection<String> rows = zset(workerGroup, slotNo)
                .valueRange(0, true, now.getEpochSecond(), true, 0, Math.max(1, limit));
        return List.copyOf(rows);
    }

    private RScoredSortedSet<String> zset(String workerGroup, int slotNo) {
        if (workerGroup == null || !GROUP.matcher(workerGroup).matches()) {
            throw new IllegalArgumentException("invalid workflow workerGroup");
        }
        if (slotNo < 0) {
            throw new IllegalArgumentException("slotNo must be >= 0");
        }
        return redisson.getScoredSortedSet("workflow:ready:" + workerGroup + ":slot:" + slotNo);
    }
}
