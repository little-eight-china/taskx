package io.taskx.meta.redis;

import io.taskx.core.slot.SlotRedisKeys;
import io.taskx.core.store.DueMember;
import io.taskx.core.store.TriggerIndex;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.protocol.ScoredEntry;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class RedissonTriggerIndex implements TriggerIndex {

    private final RedissonClient redisson;

    public RedissonTriggerIndex(RedissonClient redisson) {
        this.redisson = Objects.requireNonNull(redisson);
    }

    @Override
    public void add(int slotNo, String taskId, long scoreEpochSecond) {
        zset(slotNo).add((double) scoreEpochSecond, taskId);
    }

    @Override
    public void remove(int slotNo, String taskId) {
        zset(slotNo).remove(taskId);
    }

    @Override
    public List<DueMember> rangeDue(int slotNo, long nowEpochSecond, int limit) {
        Collection<ScoredEntry<String>> entries =
                zset(slotNo).entryRange(0, true, (double) nowEpochSecond, true, 0, limit);
        return entries.stream()
                .map(entry -> new DueMember(entry.getValue(), entry.getScore().longValue()))
                .toList();
    }

    @Override
    public void clear(int slotNo) {
        zset(slotNo).delete();
    }

    private RScoredSortedSet<String> zset(int slotNo) {
        return redisson.getScoredSortedSet(SlotRedisKeys.trigger(slotNo));
    }
}
