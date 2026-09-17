package io.taskx.workflow.runtime;

import io.taskx.workflow.store.WorkflowReadyIndex;
import io.taskx.workflow.store.WorkflowRuntimeStore;

import java.util.Objects;

public final class WorkflowOutboxPublisher {

    private final WorkflowRuntimeStore runtime;
    private final WorkflowReadyIndex readyIndex;

    public WorkflowOutboxPublisher(WorkflowRuntimeStore runtime, WorkflowReadyIndex readyIndex) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.readyIndex = Objects.requireNonNull(readyIndex, "readyIndex");
    }

    public int publishBatch(int limit) {
        int published = 0;
        for (OutboxEvent event : runtime.findUnpublishedOutbox(limit)) {
            // ZADD is idempotent. A crash before markPublished only repeats this write.
            readyIndex.add(
                    event.workerGroup(),
                    event.slotNo(),
                    event.activationId(),
                    event.availableAt()
            );
            runtime.markOutboxPublished(event.id());
            published++;
        }
        return published;
    }
}
