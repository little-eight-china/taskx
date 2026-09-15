package io.taskx.admin.web;

import io.taskx.admin.web.dto.SlotAssignRequest;
import io.taskx.admin.web.dto.SlotView;
import io.taskx.core.domain.SlotOwnership;
import io.taskx.core.maintenance.SlotReassignService;
import io.taskx.core.slot.SlotConfig;
import io.taskx.core.store.SlotOwnershipRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/slots")
public class SlotController {

    private final SlotOwnershipRepository ownership;
    private final SlotReassignService reassign;
    private final SlotConfig slots;

    public SlotController(
            SlotOwnershipRepository ownership,
            SlotReassignService reassign,
            SlotConfig slots
    ) {
        this.ownership = ownership;
        this.reassign = reassign;
        this.slots = slots;
    }

    @GetMapping
    public List<SlotView> list() {
        Map<Integer, String> bySlot = ownership.listAll().stream()
                .collect(Collectors.toMap(SlotOwnership::slotNo, SlotOwnership::executorId));
        List<SlotView> rows = new ArrayList<>(slots.slotCount());
        for (int slotNo = 0; slotNo < slots.slotCount(); slotNo++) {
            rows.add(new SlotView(slotNo, bySlot.get(slotNo)));
        }
        return rows;
    }

    @PutMapping("/{slotNo}")
    public SlotView assign(@PathVariable int slotNo, @RequestBody SlotAssignRequest body) {
        if (body == null || body.executorId() == null || body.executorId().isBlank()) {
            throw new IllegalArgumentException("executorId is required");
        }
        reassign.reassign(slotNo, body.executorId());
        return new SlotView(slotNo, body.executorId());
    }
}
