package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.document.PreviousDecisionsPolicy;
import io.github.leontecristian.workflow.core.dto.definition.document.TaskResultContract;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.PreviousDecision;
import io.github.leontecristian.workflow.core.dto.runtime.PreviousDecisionPage;
import io.github.leontecristian.workflow.core.dto.runtime.TaskClosureReason;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowActivityEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowHistoryEntity;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import io.github.leontecristian.workflow.core.repository.runtime.PreviousDecisionRow;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowActivityRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowHistoryRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowTaskRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;

/** Retained facts determine membership; only the narrow attribution leaves this reader. */
final class RuntimePreviousDecisions {
    private static final int PAGE_BYTES = 256 * 1024;
    private final WorkflowActivityRepository activities;
    private final WorkflowTaskRepository tasks;
    private final WorkflowHistoryRepository history;
    private final RuntimeJson json;

    RuntimePreviousDecisions(WorkflowActivityRepository activities, WorkflowTaskRepository tasks,
            WorkflowHistoryRepository history, RuntimeJson json) {
        this.activities = activities; this.tasks = tasks; this.history = history; this.json = json;
    }

    PreviousDecisionPage read(WorkflowActivityEntity receiving, MinimalWorkflowDefinition model, long after, int limit) {
        var human = model.humans().get(receiving.getNodeKey());
        require(human != null);
        var policy = human.previousDecisions();
        if (policy.mode() == PreviousDecisionsPolicy.Mode.NONE) return new PreviousDecisionPage(List.of(), null);
        var entered = uniqueFact(receiving, "ACTIVITY_ENTERED");
        var entry = json.tree(entered.getDetailsJson());
        require(entered.getId().getSequenceNumber().equals(receiving.getActivationSequence())
                && entered.getOccurredAt().equals(receiving.getEnteredAt())
                && entered.getDetailsSchemaVersion() == 2 && entry.isObject()
                && receiving.getNodeKey().equals(entry.path("nodeKey").textValue())
                && Long.toString(receiving.getActivationSequence()).equals(entry.path("activationSequence").textValue())
                && Long.toString(receiving.getTaskTypeId()).equals(entry.path("taskTypeId").textValue())
                && receiving.getCompletionPolicy().name().equals(entry.path("completionPolicy").textValue())
                && entry.path("requiredTaskCount").isIntegralNumber()
                && entry.path("requiredTaskCount").intValue() == receiving.getRequiredTaskCount());
        long cutoff = entered.getId().getSequenceNumber();
        if (after >= cutoff) return new PreviousDecisionPage(List.of(), null);
        var keys = new HashSet<>(policy.mode() == PreviousDecisionsPolicy.Mode.SELECTED
                ? policy.nodeKeys() : model.humans().keySet());
        keys.remove(receiving.getNodeKey());
        if (keys.isEmpty()) return new PreviousDecisionPage(List.of(), null);

        // Retain only bounded graph metadata, never all history or submissions. SQL filters sources/cutoff.
        var sources = new HashMap<Long, WorkflowActivityEntity>();
        var completedAt = new HashMap<Long, Long>();
        var seenNodes = new HashSet<String>();
        long cursor = 0;
        while (true) {
            var batch = activities.findPreviousSources(receiving.getInstanceId(), receiving.getId(), keys,
                    cutoff, cursor, PageRequest.of(0, 25));
            for (var source : batch) {
                require(source.getId() > cursor && source.getInstanceId().equals(receiving.getInstanceId())
                        && source.getStatus() == io.github.leontecristian.workflow.core.dto.runtime.ActivityStatus.COMPLETED
                        && keys.contains(source.getNodeKey()) && seenNodes.add(source.getNodeKey()));
                var completed = uniqueFact(source, "ACTIVITY_COMPLETED");
                var details = json.tree(completed.getDetailsJson());
                require(completed.getDetailsSchemaVersion() == 2 && details.isObject() && details.size() == 2
                        && Objects.equals(source.getOutcomeKey(), details.path("outcomeKey").textValue())
                        && "TASK_OUTCOME".equals(details.path("closureReason").textValue())
                        && "TASK_OUTCOME".equals(source.getClosureReason())
                        && Objects.equals(source.getClosedAt(), completed.getOccurredAt())
                        && Objects.equals(source.getClosedBy(), completed.getActorRef())
                        && completed.getActorKind() == ActorKind.USER
                        && completed.getId().getSequenceNumber() > source.getActivationSequence()
                        && completed.getId().getSequenceNumber() < cutoff);
                sources.put(source.getId(), source);
                completedAt.put(source.getId(), completed.getId().getSequenceNumber());
                cursor = source.getId();
            }
            if (batch.size() < 25) break;
        }
        if (sources.isEmpty()) return new PreviousDecisionPage(List.of(), null);
        // A history-only join would silently drop a completed task whose retained fact is missing.
        require(tasks.findPreviousTasksWithoutUniqueFact(sources.keySet(), PageRequest.of(0, 1)).isEmpty());
        var rows = history.findPreviousDecisionPage(sources.keySet(), after, PageRequest.of(0, limit + 1));
        var items = new ArrayList<PreviousDecision>();
        for (int i = 0; i < Math.min(limit, rows.size()); i++) {
            var row = rows.get(i);
            var source = sources.get(row.task().getActivityId());
            var item = decision(row, source, model.humans().get(source.getNodeKey()), completedAt.get(source.getId()));
            require(item.historySequence() > after && (items.isEmpty()
                    || item.historySequence() > items.get(items.size() - 1).historySequence()));
            RuntimeInputs.require(fits(new PreviousDecisionPage(List.of(item), item.historySequence())), Category.LIMIT_EXCEEDED);
            items.add(item);
            Long next = i + 1 < rows.size() ? item.historySequence() : null;
            if (!fits(new PreviousDecisionPage(items, next))) {
                items.remove(items.size() - 1);
                return new PreviousDecisionPage(items, items.get(items.size() - 1).historySequence());
            }
        }
        return new PreviousDecisionPage(items, rows.size() > limit ? items.get(items.size() - 1).historySequence() : null);
    }

    private WorkflowHistoryEntity uniqueFact(WorkflowActivityEntity activity, String event) {
        var facts = history.findActivityFacts(activity.getId(), event, PageRequest.of(0, 2));
        require(facts.size() == 1);
        var fact = facts.get(0);
        require(activity.getId().equals(fact.getActivityId()) && activity.getInstanceId().equals(fact.getId().getInstanceId())
                && event.equals(fact.getEventType()) && fact.getTaskId() == null);
        return fact;
    }

    private PreviousDecision decision(PreviousDecisionRow row, WorkflowActivityEntity activity,
            MinimalWorkflowDefinition.Human human, long completionSequence) {
        var task = row.task(); var fact = row.fact();
        short version = fact.getDetailsSchemaVersion();
        require(version == 2 || version == 3);
        var details = json.tree(fact.getDetailsJson());
        require(details.isObject() && details.size() == (version == 3 ? 4 : 3)
                && (version == 2 || details.path("result").isObject())
                && details.path("outcomeKey").isTextual() && details.path("inputs").isObject() && details.path("inputs").isEmpty()
                && details.has("comment") && (details.get("comment").isNull()
                    || details.get("comment").isTextual() && RuntimeInputs.text(details.get("comment").textValue(), 2000, 2000, false))
                && (version == 3 ? human.resultContract().mode() != TaskResultContract.Mode.FORBIDDEN
                    : human.resultContract().mode() != TaskResultContract.Mode.REQUIRED)
                && task.getStatus() == io.github.leontecristian.workflow.core.dto.runtime.TaskStatus.COMPLETED
                && task.getClosureReason() == TaskClosureReason.TASK_OUTCOME && fact.getActorKind() == ActorKind.USER
                && RuntimeInputs.text(fact.getActorRef(), 200, 200, true)
                && Objects.equals(task.getClosedBy(), fact.getActorRef())
                && (task.getAssignmentKind() != io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind.USER
                    || Objects.equals(task.getAssignmentRef(), fact.getActorRef()))
                && Objects.equals(task.getClosedAt(), fact.getOccurredAt())
                && !fact.getOccurredAt().isBefore(task.getCreatedAt()) && !fact.getOccurredAt().isBefore(activity.getEnteredAt())
                && task.getId().equals(fact.getTaskId()) && activity.getId().equals(fact.getActivityId())
                && activity.getInstanceId().equals(fact.getId().getInstanceId()) && "TASK_COMPLETED".equals(fact.getEventType())
                && fact.getId().getSequenceNumber() > activity.getActivationSequence()
                && fact.getId().getSequenceNumber() < completionSequence
                && Objects.equals(task.getOutcomeKey(), details.path("outcomeKey").textValue()));
        var outcome = human.outcome(task.getOutcomeKey());
        require(outcome.isPresent());
        return new PreviousDecision(task.getId(), activity.getId(), human.key(), human.name(), outcome.get().key(),
                outcome.get().label(), fact.getActorKind(), fact.getActorRef(), RuntimeViews.instant(fact.getOccurredAt()),
                fact.getId().getSequenceNumber(), details.get("comment").isNull() ? null : details.get("comment").textValue());
    }

    private boolean fits(PreviousDecisionPage page) {
        String encoded = json.encode(page);
        return encoded.length() * 2L <= PAGE_BYTES && RuntimeJson.utf8Fits(encoded, PAGE_BYTES);
    }

    private static void require(boolean valid) { RuntimeInputs.require(valid, Category.HOST_CONFIGURATION); }
}
