package io.taskx.core.fake;

import io.taskx.core.store.DueMember;
import io.taskx.core.store.TriggerIndex;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryTriggerIndex implements TriggerIndex {

    private final ConcurrentHashMap<Integer, ConcurrentHashMap<String, Long>> slots = new ConcurrentHashMap<>();

    @Override
    public void add(int slotNo, String taskId, long scoreEpochSecond) {
        slots.computeIfAbsent(slotNo, key -> new ConcurrentHashMap<>()).put(taskId, scoreEpochSecond);
    }

    @Override
    public void remove(int slotNo, String taskId) {
        ConcurrentHashMap<String, Long> members = slots.get(slotNo);
        if (members != null) {
            members.remove(taskId);
        }
    }

    @Override
    public List<DueMember> rangeDue(int slotNo, long nowEpochSecond, int limit) {
        ConcurrentHashMap<String, Long> members = slots.get(slotNo);
        if (members == null) {
            return List.of();
        }
        return members.entrySet().stream()
                .filter(entry -> entry.getValue() <= nowEpochSecond)
                .sorted(Comparator.comparingLong(Map.Entry::getValue))
                .limit(limit)
                .map(entry -> new DueMember(entry.getKey(), entry.getValue()))
                .toList();
    }

    public Long score(int slotNo, String taskId) {
        ConcurrentHashMap<String, Long> members = slots.get(slotNo);
        return members == null ? null : members.get(taskId);
    }
}
