package io.taskx.workflow.definition;

import io.taskx.workflow.store.WorkflowDefinitionStore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.NoSuchElementException;
import java.util.Objects;

public final class WorkflowPublishingService {

    private final WorkflowDefinitionStore store;
    private final WorkflowDefinitionCodec codec;
    private final WorkflowDefinitionValidator validator;
    private final Clock clock;

    public WorkflowPublishingService(
            WorkflowDefinitionStore store,
            WorkflowDefinitionCodec codec,
            WorkflowDefinitionValidator validator,
            Clock clock
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public WorkflowVersion publish(String workflowId, long expectedDraftRevision) {
        WorkflowDraft draft = store.findDraft(workflowId)
                .orElseThrow(() -> new NoSuchElementException("workflow draft not found: " + workflowId));
        if (draft.revision() != expectedDraftRevision) {
            throw new IllegalStateException(
                    "workflow draft changed: expected revision " + expectedDraftRevision + ", was " + draft.revision());
        }
        WorkflowDefinition definition = codec.decode(draft.definitionJson());
        validator.validate(definition);
        String canonicalJson = codec.encode(definition);
        String hash = sha256(canonicalJson);
        Instant now = clock.instant();
        return store.publish(workflowId, expectedDraftRevision, hash, now);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
