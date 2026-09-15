package io.taskx.core.fake;

import io.taskx.core.domain.Task;
import io.taskx.core.store.TaskRepository;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryTaskRepository implements TaskRepository {

    private final ConcurrentHashMap<String, Task> tasks = new ConcurrentHashMap<>();

    @Override
    public Optional<Task> findById(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    @Override
    public void save(Task task) {
        tasks.put(Objects.requireNonNull(task).id(), task);
    }

    @Override
    public void delete(String taskId) {
        tasks.remove(taskId);
    }
}
