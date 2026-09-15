package io.taskx.admin.web;

import io.taskx.admin.web.dto.TaskResponse;
import io.taskx.admin.web.dto.TaskWriteRequest;
import io.taskx.core.config.TaskConfigService;
import io.taskx.core.domain.Task;
import io.taskx.core.slot.SlotConfig;
import io.taskx.core.store.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskRepository tasks;
    private final TaskConfigService configs;
    private final SlotConfig slots;

    public TaskController(TaskRepository tasks, TaskConfigService configs, SlotConfig slots) {
        this.tasks = tasks;
        this.configs = configs;
        this.slots = slots;
    }

    @GetMapping
    public List<TaskResponse> list() {
        return tasks.findAll().stream().map(task -> TaskResponse.from(task, slots)).toList();
    }

    @GetMapping("/{id}")
    public TaskResponse get(@PathVariable String id) {
        return TaskResponse.from(require(id), slots);
    }

    @PutMapping("/{id}")
    public TaskResponse put(@PathVariable String id, @RequestBody TaskWriteRequest body) {
        configs.save(body.toTask(id));
        return TaskResponse.from(require(id), slots);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        configs.delete(id);
    }

    @PostMapping("/{id}/disable")
    public TaskResponse disable(@PathVariable String id) {
        configs.disable(id);
        return TaskResponse.from(require(id), slots);
    }

    @PostMapping("/{id}/enable")
    public TaskResponse enable(@PathVariable String id) {
        configs.enable(id);
        return TaskResponse.from(require(id), slots);
    }

    private Task require(String id) {
        return tasks.findById(id).orElseThrow(() -> new NoSuchElementException("task not found: " + id));
    }
}
