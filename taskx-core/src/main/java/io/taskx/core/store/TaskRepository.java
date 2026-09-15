package io.taskx.core.store;

import io.taskx.core.domain.Task;

import java.util.List;
import java.util.Optional;

public interface TaskRepository {

    Optional<Task> findById(String taskId);

    List<Task> findAll();

    void save(Task task);

    void delete(String taskId);
}
