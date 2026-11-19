package io.github.leontecristian.workflow.testhost.service.runtime;

import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.decimal;
import static io.github.leontecristian.workflow.testhost.service.runtime.ConsoleHistoryReferences.Kind.EVENT;
import static io.github.leontecristian.workflow.testhost.service.runtime.ConsoleHistoryReferences.Kind.PAGE;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistorySummary;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/** Projects retained events after current instance and exact-event authorization. */
@Transactional(readOnly = true)
public class ConsoleBusinessHistory {
    private final ConsoleRuntimeAccess access;
    private final ConsoleViewerAccess viewer;
    private final WorkflowRuntimeQueryService reads;
    private final WorkflowDefinitionService definitions;
    private final ConsoleRuntimeReadHost directory;
    private final ConsoleWire wire;
    private final ConsoleHistoryReferences references = new ConsoleHistoryReferences(16_384);

    public ConsoleBusinessHistory(ConsoleRuntimeAccess access, ConsoleViewerAccess viewer, WorkflowRuntimeQueryService reads,
            WorkflowDefinitionService definitions, ConsoleRuntimeReadHost directory, ConsoleWire wire) {
        this.access = access;
        this.viewer = viewer;
        this.reads = reads;
        this.definitions = definitions;
        this.directory = directory;
        this.wire = wire;
    }

    public record Summary(String eventRef, String label, String actorLabel, Instant occurredAt) {}
    public record Page(String representation, List<Summary> items, String nextCursor) {}
    public record Event(String representation, Summary summary, String stepName, String outcomeLabel, String comment, Long taskId) {}

    public Page page(String actor, long id, String cursor, int limit) {
        access.instance(actor, id);
        long after = cursor == null ? 0 : references.resolve(PAGE, actor, id, cursor);
        var page = reads.listHistory(id, after, limit);
        var items = new ArrayList<Summary>();
        for (var event : page.items()) {
            if (visible(actor, event)) items.add(summary(actor, id, event));
        }
        String next = page.nextAfterSequence() == null ? null : references.issue(PAGE, actor, id, page.nextAfterSequence());
        return new Page("business", items, next);
    }

    public Event event(String actor, long id, String ref) {
        var instance = access.instance(actor, id);
        // Previously shared browser links use decimal positions. Authorize them identically.
        long sequence;
        try { sequence = ref.matches("[1-9][0-9]*") ? decimal(ref, false) : references.resolve(EVENT, actor, id, ref); }
        catch (ConsoleException invalid) { throw ConsoleException.missing(); }
        var summary = reads.listHistory(id, sequence - 1, 1).items().stream()
                .filter(candidate -> candidate.historySequence() == sequence && visible(actor, candidate))
                .findFirst().orElseThrow(ConsoleException::missing);
        boolean completion = "TASK_COMPLETED".equals(summary.eventType());
        boolean automaticStep = "SERVICE_TASK_COMPLETED".equals(summary.eventType());
        var detail = completion || automaticStep || "TASK_CREATED".equals(summary.eventType())
                ? reads.findHistoryEvent(id, sequence, candidate -> viewer.canReadExactTask(actor, candidate))
                        .orElseThrow(ConsoleException::missing)
                : null;
        var details = completion || automaticStep ? wire.tree(detail.detailsJson()) : null;
        String nodeKey = automaticStep ? details.path("nodeKey").textValue()
                : summary.activityId() == null ? summary.targetNodeKey()
                : reads.findActivity(summary.activityId()).map(activity -> activity.nodeKey()).orElse(null);
        var node = node(instance.definitionVersionId(), nodeKey);
        String name = node == null ? null : node.path("name").textValue();
        String outcome = null;
        String comment = null;
        if (completion) {
            String key = details.path("outcomeKey").textValue();
            comment = details.path("comment").textValue();
            if (node != null && key != null) {
                for (var option : node.path("outcomes")) {
                    if (key.equals(option.path("key").textValue())) {
                        outcome = option.path("label").textValue();
                        break;
                    }
                }
            }
        }
        return new Event("business", summary(actor, id, summary), name, outcome, comment, summary.taskId());
    }

    private boolean visible(String actor, WorkflowHistorySummary event) {
        return label(event.eventType()) != null && (event.taskId() == null
                ? !event.eventType().startsWith("TASK_") : viewer.canReadExactTask(actor, event));
    }

    private Summary summary(String actor, long id, WorkflowHistorySummary event) {
        String person = directory.userLabel(event.actorRef());
        String label = event.actorKind() == ActorKind.SYSTEM ? "System"
                : actor.equals(event.actorRef()) ? "You" : person == null ? "Reviewer" : person;
        return new Summary(references.issue(EVENT, actor, id, event.historySequence()), label(event.eventType()),
                label, event.occurredAt());
    }

    private JsonNode node(long versionId, String key) {
        if (key == null) return null;
        var version = definitions.findVersion(versionId).orElseThrow();
        for (var node : wire.tree(version.documentJson()).path("nodes")) if (key.equals(node.path("key").textValue())) return node;
        return null;
    }

    private static String label(String type) {
        return switch (type) {
            case "INSTANCE_STARTED" -> "Workflow started";
            case "INSTANCE_COMPLETED" -> "Workflow completed";
            case "INSTANCE_CANCELLED" -> "Workflow cancelled";
            case "ACTIVITY_ENTERED" -> "Review opened";
            case "ACTIVITY_COMPLETED" -> "Review resolved";
            case "ACTIVITY_CANCELLED" -> "Review cancelled";
            case "TASK_CREATED" -> "Task opened";
            case "TASK_COMPLETED" -> "Decision recorded";
            case "TASK_CANCELLED" -> "Task cancelled";
            case "TASK_CLAIMED" -> "Task claimed";
            case "TASK_RELEASED" -> "Task released";
            case "TASK_REASSIGNED" -> "Task reassigned";
            case "SERVICE_TASK_COMPLETED" -> "Automatic step completed";
            default -> null;
        };
    }
}
