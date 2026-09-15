package io.taskx.admin.web;

import io.taskx.admin.ExecutionCommandService;
import io.taskx.admin.web.dto.ExecutionResponse;
import io.taskx.core.domain.ExecutionStatus;
import io.taskx.core.store.ExecutionRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/executions")
public class ExecutionController {

    private final ExecutionRepository executions;
    private final ExecutionCommandService commands;

    public ExecutionController(ExecutionRepository executions, ExecutionCommandService commands) {
        this.executions = executions;
        this.commands = commands;
    }

    @GetMapping
    public List<ExecutionResponse> list(
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) ExecutionStatus status,
            @RequestParam(required = false) Integer limit
    ) {
        int size = limit == null ? 50 : limit;
        if (size < 1 || size > 200) {
            throw new IllegalArgumentException("limit must be 1..200");
        }
        return executions.list(taskId, status, size).stream().map(ExecutionResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ExecutionResponse get(@PathVariable long id) {
        return ExecutionResponse.from(executions.findById(id)
                .orElseThrow(() -> new NoSuchElementException("execution not found: " + id)));
    }

    @PostMapping("/{id}/requeue")
    public ExecutionResponse requeue(@PathVariable long id) {
        return ExecutionResponse.from(commands.requeue(id));
    }

    @PostMapping("/{id}/cancel")
    public ExecutionResponse cancel(@PathVariable long id) {
        return ExecutionResponse.from(commands.cancel(id));
    }
}
