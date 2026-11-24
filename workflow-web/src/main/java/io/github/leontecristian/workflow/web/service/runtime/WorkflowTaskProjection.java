package io.github.leontecristian.workflow.web.service.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.PreviousDecision;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.TaskSubmission;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskAdministrationItem;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowAuthorizedRuntimeReadService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import io.github.leontecristian.workflow.web.integration.runtime.WorkflowAssignmentDirectory;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Request-local projection of task records after resource access was established. */
public final class WorkflowTaskProjection {
    private final WorkflowRuntimeQueryService reads;
    private final WorkflowAuthorizedRuntimeReadService access;
    private final WorkflowDefinitionService definitions;
    private final TaskTypeCatalogService catalog;
    private final WorkflowAssignmentDirectory directory;
    private final RuntimeWire wire;
    private final String actorRef;
    private final Map<Long, String> activityNodes = new HashMap<>();
    private final Map<Long, Long> instanceVersions = new HashMap<>();
    private final Map<Long, String> taskTypeNames = new HashMap<>();
    private final Map<Long, PinnedDefinition> pinnedDefinitions = new HashMap<>();

    private record PinnedDefinition(String name, Map<String, JsonNode> nodes) {}

    public WorkflowTaskProjection(WorkflowRuntimeQueryService reads, WorkflowAuthorizedRuntimeReadService access,
            WorkflowDefinitionService definitions,
            TaskTypeCatalogService catalog, WorkflowAssignmentDirectory directory, RuntimeWire wire, String actorRef) {
        this.reads = reads;
        this.access = access;
        this.definitions = definitions;
        this.catalog = catalog;
        this.directory = directory;
        this.wire = wire;
        this.actorRef = actorRef;
    }

    public JsonNode node(WorkflowTaskView task) {
        long versionId = instanceVersions.computeIfAbsent(task.instanceId(), id ->
                reads.findInstance(id).orElseThrow().definitionVersionId());
        return node(task, versionId);
    }

    public JsonNode node(WorkflowTaskView task, long versionId) {
        String nodeKey = activityNodes.computeIfAbsent(task.activityId(), id ->
                reads.findActivity(id).orElseThrow().nodeKey());
        return node(task, versionId, nodeKey);
    }

    public JsonNode node(WorkflowTaskView task, long versionId, String nodeKey) {
        activityNodes.put(task.activityId(), nodeKey);
        instanceVersions.put(task.instanceId(), versionId);
        var node = pinnedDefinition(versionId).nodes().get(nodeKey);
        if (node == null) throw new IllegalStateException("Pinned human contract missing");
        return node;
    }

    public String workflowName(long versionId) {
        return pinnedDefinition(versionId).name();
    }

    private PinnedDefinition pinnedDefinition(long versionId) {
        return pinnedDefinitions.computeIfAbsent(versionId, id -> {
            var version = definitions.findVersion(id).orElseThrow(() -> new IllegalStateException("Pinned definition missing"));
            var document = wire.tree(version.documentJson());
            Map<String, JsonNode> nodes = new HashMap<>();
            for (var node : document.path("nodes")) nodes.put(node.path("key").asText(), node);
            return new PinnedDefinition(display(document.path("name").textValue()), nodes);
        });
    }

    public Map<String, Object> task(WorkflowTaskView task, boolean navigation) {
        return task(task, node(task), navigation);
    }

    public Map<String, Object> task(WorkflowTaskView task, JsonNode node, boolean navigation) {
        String typeName = taskTypeNames.computeIfAbsent(task.taskTypeId(), id ->
                catalog.findById(id).map(type -> type.name()).orElse(null));
        return task(task, node.path("name").textValue(), typeName, outcomeLabel(node, task.outcomeKey()), navigation);
    }

    public Map<String, Object> administrationTask(WorkflowTaskAdministrationItem item) {
        var task = item.task();
        String name = item.nodeKey().equals(item.nodeName()) ? null : item.nodeName();
        var result = task(task, name, item.taskTypeName(), null, true);
        result.put("instanceId", task.instanceId());
        result.put("createdAt", task.createdAt());
        return result;
    }

    public Map<String, Object> personalTask(WorkflowTaskView task) {
        var result = task(task, true);
        result.put("createdAt", task.createdAt());
        return result;
    }

    private Map<String, Object> task(WorkflowTaskView task, String nodeName, String typeName, String outcomeLabel,
            boolean navigation) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (navigation) result.put("id", task.id());
        put(result, "nodeName", display(nodeName));
        put(result, "taskTypeName", display(typeName));
        result.put("status", task.status());
        put(result, "dueAt", task.dueAt());
        put(result, "closedAt", task.closedAt());
        put(result, "assignmentLabel", assignmentLabel(task));
        put(result, "ownershipLabel", ownershipLabel(task));
        put(result, "outcomeLabel", outcomeLabel);
        if (task.status() == TaskStatus.CANCELLED && task.closureReason() != null) {
            String message = switch (task.closureReason()) {
                case INSTANCE_CANCELLED -> "The workflow was cancelled.";
                case ACTIVITY_RESOLVED, REVIEW_SUPERSEDED -> "The review resolved before this task was completed.";
                case TASK_OUTCOME -> null;
            };
            put(result, "closureLabel", message);
        }
        return result;
    }

    public Map<String, Object> submission(TaskSubmission submission, WorkflowTaskView task, boolean navigation) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (navigation) result.put("taskId", submission.taskId());
        put(result, "outcomeLabel", outcomeLabel(node(task), submission.outcomeKey()));
        result.put("actorLabel", decisionActor(submission.actorKind(), submission.actorRef(), task));
        result.put("occurredAt", submission.occurredAt());
        put(result, "comment", submission.comment());
        put(result, "result", submission.result());
        return result;
    }

    public Map<String, Object> previousDecision(PreviousDecision decision, WorkflowTaskView receivingTask) {
        Map<String, Object> result = new LinkedHashMap<>();
        String name = decision.nodeName();
        result.put("nodeName", name == null || name.isBlank() || name.equals(decision.nodeKey()) ? "Task" : name);
        result.put("outcomeLabel", decision.outcomeLabel());
        result.put("actorLabel", previousActorLabel(decision, receivingTask));
        result.put("occurredAt", decision.occurredAt());
        put(result, "comment", decision.comment());
        return result;
    }

    private String assignmentLabel(WorkflowTaskView task) {
        if (task.assignment().kind() == AssignmentTargetKind.USER) return null;
        var label = directoryLabel(task);
        return label == null ? "Shared task" : label;
    }

    private String ownershipLabel(WorkflowTaskView task) {
        String owner = task.assigneeRef();
        if (task.assignment().kind() == AssignmentTargetKind.USER) {
            if (owner == null && task.status() != TaskStatus.OPEN) owner = task.assignment().ref();
            if (actorRef.equals(owner)) return "Assigned to you";
            String name = owner != null && owner.equals(task.assignment().ref()) ? directoryLabel(task) : null;
            return name == null ? "Assigned to another user" : "Assigned to " + name;
        }
        if (owner == null) return null;
        return actorRef.equals(owner) ? "Claimed by you" : "Claimed by another user";
    }

    private String decisionActor(ActorKind kind, String ref, WorkflowTaskView authorizedTask) {
        if (kind == ActorKind.SYSTEM) return "System";
        if (actorRef.equals(ref)) return "You";
        var label = personLabel(ref, authorizedTask);
        return label == null ? "Reviewer" : label;
    }

    private String previousActorLabel(PreviousDecision decision, WorkflowTaskView receivingTask) {
        if (decision.actorKind() == ActorKind.SYSTEM) return "System";
        if (actorRef.equals(decision.actorRef())) return "You";
        var label = personLabel(decision.actorRef(), receivingTask);
        if (label != null) return label;
        try {
            // A previous-decision grant does not grant the source task. Enrich attribution only
            // when the original read policy separately permits this exact source.
            var source = access.task(actorRef, decision.taskId());
            if (source != null && source.instanceId() == receivingTask.instanceId()
                    && source.activityId() == decision.activityId()) label = personLabel(decision.actorRef(), source);
        } catch (RuntimeException unavailable) {
            return "Reviewer";
        }
        return label == null ? "Reviewer" : label;
    }

    private String personLabel(String ref, WorkflowTaskView authorizedTask) {
        if (authorizedTask != null && ref != null
                && authorizedTask.assignment().kind() == AssignmentTargetKind.USER
                && ref.equals(authorizedTask.assignment().ref())) {
            return directoryLabel(authorizedTask);
        }
        return null;
    }

    private String directoryLabel(WorkflowTaskView task) {
        String rawRef = task.assignment().ref();
        String label;
        try { label = display(directory.assignmentLabel(task)); }
        catch (RuntimeException unavailable) { return null; }
        return label != null && rawRef != null && label.strip().equalsIgnoreCase(rawRef.strip()) ? null : label;
    }

    private static String display(String label) {
        return label == null || label.isBlank() || label.length() > 200 || !RuntimeWire.unicode(label) ? null : label;
    }

    private static String outcomeLabel(JsonNode node, String key) {
        if (key == null) return null;
        for (var outcome : node.path("outcomes")) {
            if (key.equals(outcome.path("key").textValue())) return display(outcome.path("label").textValue());
        }
        return null;
    }

    private static void put(Map<String, Object> result, String field, Object value) {
        if (value != null) result.put(field, value);
    }
}
