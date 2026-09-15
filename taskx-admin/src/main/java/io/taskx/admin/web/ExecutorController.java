package io.taskx.admin.web;

import io.taskx.core.store.ExecutorRegistry;
import io.taskx.core.store.ExecutorView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/executors")
public class ExecutorController {

    private final ExecutorRegistry executors;

    public ExecutorController(ExecutorRegistry executors) {
        this.executors = executors;
    }

    @GetMapping
    public List<ExecutorView> list() {
        return executors.list();
    }
}
