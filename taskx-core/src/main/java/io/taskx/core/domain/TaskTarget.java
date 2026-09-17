package io.taskx.core.domain;

import io.taskx.core.Require;

public sealed interface TaskTarget {

    Type type();

    enum Type {
        HANDLER,
        WORKFLOW
    }

    record Handler(String name) implements TaskTarget {
        public Handler {
            name = Require.notBlank(name, "handler name");
        }

        @Override
        public Type type() {
            return Type.HANDLER;
        }
    }

    /**
     * {@code pinnedVersion == null} resolves current_published_version when the workflow starts.
     */
    record Workflow(String workflowId, Integer pinnedVersion) implements TaskTarget {
        public Workflow {
            workflowId = Require.notBlank(workflowId, "workflowId");
            if (pinnedVersion != null && pinnedVersion <= 0) {
                throw new IllegalArgumentException("pinnedVersion must be > 0");
            }
        }

        @Override
        public Type type() {
            return Type.WORKFLOW;
        }
    }
}
