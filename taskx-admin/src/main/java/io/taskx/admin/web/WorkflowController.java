package io.taskx.admin.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.taskx.admin.web.dto.WorkflowDraftRequest;
import io.taskx.admin.web.dto.WorkflowDraftResponse;
import io.taskx.admin.web.dto.WorkflowPublishRequest;
import io.taskx.admin.web.dto.WorkflowVersionResponse;
import io.taskx.workflow.definition.WorkflowDraft;
import io.taskx.workflow.definition.WorkflowPublishingService;
import io.taskx.workflow.definition.WorkflowVersion;
import io.taskx.workflow.store.WorkflowDefinitionStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowDefinitionStore store;
    private final WorkflowPublishingService publishing;
    private final ObjectMapper mapper;

    public WorkflowController(
            WorkflowDefinitionStore store,
            WorkflowPublishingService publishing,
            ObjectMapper mapper
    ) {
        this.store = store;
        this.publishing = publishing;
        this.mapper = mapper;
    }

    @GetMapping("/{workflowId}/draft")
    public WorkflowDraftResponse draft(@PathVariable String workflowId) {
        return draftResponse(store.findDraft(workflowId)
                .orElseThrow(() -> new NoSuchElementException("workflow draft not found: " + workflowId)));
    }

    @PutMapping("/{workflowId}/draft")
    public WorkflowDraftResponse saveDraft(
            @PathVariable String workflowId,
            @RequestBody WorkflowDraftRequest request
    ) {
        if (request == null || request.definition() == null) {
            throw new IllegalArgumentException("definition is required");
        }
        WorkflowDraft saved = store.saveDraft(
                workflowId,
                request.name(),
                request.expectedRevision(),
                write(request.definition()),
                request.uiLayout() == null || request.uiLayout().isNull()
                        ? null
                        : write(request.uiLayout())
        );
        return draftResponse(saved);
    }

    @PostMapping("/{workflowId}/publish")
    public WorkflowVersionResponse publish(
            @PathVariable String workflowId,
            @RequestBody WorkflowPublishRequest request
    ) {
        WorkflowVersion version = publishing.publish(workflowId, request.expectedRevision());
        return versionResponse(version);
    }

    @GetMapping("/{workflowId}/published")
    public WorkflowVersionResponse currentPublished(@PathVariable String workflowId) {
        return versionResponse(store.findCurrentPublished(workflowId)
                .orElseThrow(() -> new NoSuchElementException("published workflow not found: " + workflowId)));
    }

    @GetMapping("/{workflowId}/versions/{version}")
    public WorkflowVersionResponse version(
            @PathVariable String workflowId,
            @PathVariable int version
    ) {
        return versionResponse(store.findVersion(workflowId, version)
                .orElseThrow(() -> new NoSuchElementException(
                        "workflow version not found: " + workflowId + '/' + version)));
    }

    private WorkflowDraftResponse draftResponse(WorkflowDraft draft) {
        return WorkflowDraftResponse.from(
                draft,
                read(draft.definitionJson()),
                readNullable(draft.uiLayoutJson())
        );
    }

    private WorkflowVersionResponse versionResponse(WorkflowVersion version) {
        return WorkflowVersionResponse.from(
                version,
                read(version.definitionJson()),
                readNullable(version.uiLayoutJson())
        );
    }

    private String write(JsonNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("invalid JSON", ex);
        }
    }

    private JsonNode read(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("stored workflow JSON is invalid", ex);
        }
    }

    private JsonNode readNullable(String json) {
        return json == null ? mapper.nullNode() : read(json);
    }
}
