package io.taskx.admin.web;

import io.taskx.admin.web.dto.RebuildResponse;
import io.taskx.core.maintenance.TriggerRebuildService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController {

    private final TriggerRebuildService rebuild;

    public MaintenanceController(TriggerRebuildService rebuild) {
        this.rebuild = rebuild;
    }

    @PostMapping("/rebuild-triggers")
    public RebuildResponse rebuildTriggers() {
        return new RebuildResponse(rebuild.rebuild());
    }
}
