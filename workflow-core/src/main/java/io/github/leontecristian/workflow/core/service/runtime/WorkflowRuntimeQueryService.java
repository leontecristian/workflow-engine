package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowActivityView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistorySummary;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistoryEvent;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskPage;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistoryPage;
import io.github.leontecristian.workflow.core.dto.runtime.TaskSubmissionPage;
import io.github.leontecristian.workflow.core.dto.runtime.TaskSubmission;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.TaskClosureReason;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowHistoryId;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowTaskEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowActivityEntity;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowInstanceRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowActivityRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowTaskRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowHistoryRepository;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Host-authorized detached reads. IDs are not access grants; the host authorizes instance,
 * assignee and exact evidence access before every call. Reads join the host transaction,
 * including uncommitted accepted submissions, or open a read-only REQUIRED transaction.
 * No snapshot stability across commits or implicit latest submission is promised.
 */
@Service
@Transactional(readOnly = true)
public class WorkflowRuntimeQueryService {
    private final WorkflowInstanceRepository instances;
    private final WorkflowActivityRepository activities;
    private final WorkflowTaskRepository tasks;
    private final WorkflowHistoryRepository history;
    private final RuntimeDefinitionPins pins;
    private final RuntimeJson json;
    public WorkflowRuntimeQueryService(WorkflowInstanceRepository instances, WorkflowActivityRepository activities,
            WorkflowTaskRepository tasks, WorkflowHistoryRepository history, RuntimeDefinitionPins pins, RuntimeJson json) {
        this.instances = instances; this.activities = activities; this.tasks = tasks; this.history = history; this.pins = pins; this.json = json;
    }
    public Optional<WorkflowInstanceView> findInstance(long id) {
        RuntimeInputs.id(id);
        return RuntimePersistenceErrors.call(() -> instances.findById(id).map(entity -> RuntimeViews.instance(entity, pins.identity(entity))));
    }
    public Optional<WorkflowActivityView> findActivity(long id) {
        RuntimeInputs.id(id);
        return RuntimePersistenceErrors.call(() -> activities.findById(id).map(RuntimeViews::activity));
    }
    public Optional<WorkflowTaskView> findTask(long id) {
        RuntimeInputs.id(id);
        return RuntimePersistenceErrors.call(() -> tasks.findById(id).map(entity -> RuntimeViews.task(entity, owner(entity))));
    }
    public WorkflowTaskPage listTasks(long instanceId) { return listTasks(instanceId, 0, 50); }
    public WorkflowTaskPage listTasks(long instanceId, long afterId, int limit) {
        RuntimeInputs.id(instanceId); RuntimeInputs.page(afterId, limit);
        return RuntimePersistenceErrors.call(() -> page(tasks.findByInstancePage(instanceId, afterId, PageRequest.of(0, limit + 1)),
                limit, RuntimeViews::task, WorkflowTaskView::id, WorkflowTaskPage::new));
    }
    public WorkflowTaskPage listAssignedTasks(String assignee) { return listAssignedTasks(assignee, 0, 50); }
    public WorkflowTaskPage listAssignedTasks(String assignee, long afterId, int limit) {
        RuntimeInputs.require(RuntimeInputs.text(assignee, 200, 200, true), Category.INVALID_INPUT); RuntimeInputs.page(afterId, limit);
        return RuntimePersistenceErrors.call(() -> page(tasks.findAssignedPage(assignee, assignee.length() * 2, afterId, PageRequest.of(0, limit + 1)),
                limit, RuntimeViews::task, WorkflowTaskView::id, WorkflowTaskPage::new));
    }
    public WorkflowHistoryPage listHistory(long instanceId) { return listHistory(instanceId, 0, 50); }
    public WorkflowHistoryPage listHistory(long instanceId, long afterSequence, int limit) {
        RuntimeInputs.id(instanceId); RuntimeInputs.page(afterSequence, limit);
        return RuntimePersistenceErrors.call(() -> page(history.findSummaryPage(instanceId, afterSequence, PageRequest.of(0, limit + 1)),
                limit, RuntimeViews::history, WorkflowHistorySummary::historySequence, WorkflowHistoryPage::new));
    }
    public Optional<WorkflowHistoryEvent> findHistoryEvent(long instanceId, long sequence) {
        RuntimeInputs.id(instanceId); RuntimeInputs.id(sequence);
        return RuntimePersistenceErrors.call(() -> history.findById(new WorkflowHistoryId(instanceId, sequence)).map(entity -> {
            RuntimeInputs.require(RuntimeJson.fits(entity.getDetailsJson(), 128 * 1024), Category.LIMIT_EXCEEDED);
            return new WorkflowHistoryEvent(RuntimeViews.history(entity), entity.getDetailsJson());
        }));
    }
    public Optional<TaskSubmission> findTaskSubmission(long taskId) {
        RuntimeInputs.id(taskId);
        return RuntimePersistenceErrors.call(() -> tasks.findById(taskId).filter(t -> t.getStatus() == TaskStatus.COMPLETED).map(this::submission));
    }
    public TaskSubmissionPage listActivitySubmissions(long activityId) { return listActivitySubmissions(activityId, 0, 50); }
    public TaskSubmissionPage listActivitySubmissions(long activityId, long afterTaskId, int limit) {
        RuntimeInputs.id(activityId); RuntimeInputs.page(afterTaskId, limit);
        return RuntimePersistenceErrors.call(() -> page(tasks.findCompletedPage(activityId, afterTaskId, PageRequest.of(0, limit + 1)),
                limit, this::submission, TaskSubmission::taskId, TaskSubmissionPage::new));
    }
    private WorkflowActivityEntity owner(WorkflowTaskEntity task) {
        return activities.findById(task.getActivityId()).orElseThrow(() -> new WorkflowExecutionException(Category.HOST_CONFIGURATION));
    }
    private TaskSubmission submission(WorkflowTaskEntity task) {
        var activity = owner(task);
        var fact = history.findCompletionByTaskId(task.getId()).orElseThrow(() -> new WorkflowExecutionException(Category.HOST_CONFIGURATION));
        RuntimeInputs.require(fact.getDetailsSchemaVersion() == 1, Category.UNSUPPORTED_VERSION);
        var details = json.tree(fact.getDetailsJson());
        RuntimeInputs.require(details.isObject() && details.size() == 3 && details.has("outcomeKey") && details.has("comment") && details.has("inputs")
                && details.get("outcomeKey").isTextual() && details.get("inputs").isObject() && details.get("inputs").isEmpty()
                && (details.get("comment").isNull() || (details.get("comment").isTextual()
                    && RuntimeInputs.text(details.get("comment").textValue(), 2000, 2000, false))), Category.HOST_CONFIGURATION);
        RuntimeInputs.require(task.getStatus() == TaskStatus.COMPLETED && task.getClosureReason() == TaskClosureReason.TASK_OUTCOME
                && fact.getActorKind() == ActorKind.USER && Objects.equals(task.getAssignedUserRef(), fact.getActorRef())
                && Objects.equals(task.getClosedBy(), fact.getActorRef()) && task.getClosedAt() != null && task.getClosedAt().equals(fact.getOccurredAt())
                && !task.getClosedAt().isBefore(task.getCreatedAt()) && !task.getClosedAt().isBefore(activity.getEnteredAt())
                && task.getId().equals(fact.getTaskId()) && activity.getId().equals(fact.getActivityId())
                && activity.getInstanceId().equals(fact.getId().getInstanceId()) && "TASK_COMPLETED".equals(fact.getEventType())
                && RuntimeInputs.key(task.getOutcomeKey()) && Objects.equals(task.getOutcomeKey(), details.get("outcomeKey").textValue()), Category.HOST_CONFIGURATION);
        return new TaskSubmission(task.getId(), activity.getId(), activity.getInstanceId(), fact.getId().getSequenceNumber(),
                fact.getCommandId(), fact.getActorKind(), fact.getActorRef(), RuntimeViews.instant(fact.getOccurredAt()), task.getOutcomeKey(),
                details.get("comment").isNull() ? null : details.get("comment").textValue(), Map.of());
    }

    /** SQL limits rows first. JSON bytes bound the complete page including continuation; no item is split or skipped. */
    private <R,T,P> P page(List<R> rows, int limit, Function<R,T> map, ToLongFunction<T> cursor, BiFunction<List<T>,Long,P> create) {
        List<T> emitted = new ArrayList<>();
        for (int i = 0; i < Math.min(rows.size(), limit); i++) {
            T item = map.apply(rows.get(i));
            // A single item must fit its own complete page, even if this page is already nearly full.
            RuntimeInputs.require(RuntimeJson.fits(json.encode(create.apply(List.of(item), cursor.applyAsLong(item))), 1024 * 1024), Category.LIMIT_EXCEEDED);
            emitted.add(item);
            Long next = i + 1 < rows.size() ? cursor.applyAsLong(item) : null;
            P candidate = create.apply(emitted, next);
            if (!RuntimeJson.fits(json.encode(candidate), 1024 * 1024)) {
                emitted.remove(emitted.size() - 1);
                RuntimeInputs.require(!emitted.isEmpty(), Category.LIMIT_EXCEEDED);
                return create.apply(emitted, cursor.applyAsLong(emitted.get(emitted.size() - 1)));
            }
        }
        Long next = rows.size() > limit ? cursor.applyAsLong(emitted.get(emitted.size() - 1)) : null;
        return create.apply(emitted, next);
    }
}
