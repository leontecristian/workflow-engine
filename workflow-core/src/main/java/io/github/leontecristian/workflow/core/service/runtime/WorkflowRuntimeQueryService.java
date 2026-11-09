package io.github.leontecristian.workflow.core.service.runtime;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowVariablePage;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowVariableView;

import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceQuery;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceSummary;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstancePage;
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
    private final RuntimeVariables variables;
    private final WorkflowTaskEligibilityService eligibility;
    public WorkflowRuntimeQueryService(WorkflowInstanceRepository instances, WorkflowActivityRepository activities,
            WorkflowTaskRepository tasks, WorkflowHistoryRepository history, RuntimeDefinitionPins pins, RuntimeJson json, RuntimeVariables variables, WorkflowTaskEligibilityService eligibility) {
        this.eligibility=eligibility;
        this.variables=variables;
        this.instances = instances; this.activities = activities; this.tasks = tasks; this.history = history; this.pins = pins; this.json = json;
    }
    public WorkflowVariablePage listVariables(long instanceId,String afterKey,int limit,Long expectedLockVersion) {
        RuntimeInputs.id(instanceId); RuntimeInputs.require(limit>=1 && limit<=100 && (afterKey==null || (RuntimeInputs.key(afterKey) && expectedLockVersion!=null))
                && (expectedLockVersion==null || expectedLockVersion>=0),Category.INVALID_INPUT);
        long before=instances.readLockVersion(instanceId).orElseThrow(() -> new WorkflowExecutionException(Category.NOT_FOUND));
        RuntimeInputs.require(expectedLockVersion==null || expectedLockVersion==before,Category.STALE_VERSION);
        var instance=instances.findById(instanceId).orElseThrow(() -> new WorkflowExecutionException(Category.NOT_FOUND));
        var model=pins.load(instance).model();
        RuntimeInputs.require(afterKey==null || model.variables().containsKey(afterKey),Category.INVALID_INPUT);
        var values=variables.read(instanceId,model);
        var keys=model.variables().keySet().stream().sorted().filter(k -> afterKey==null || k.compareTo(afterKey)>0).toList();
        var items=new ArrayList<WorkflowVariableView>();
        String next=null;
        for(int i=0;i<Math.min(keys.size(),limit);i++) {
            String k=keys.get(i); var value=values.get(k);
            items.add(new WorkflowVariableView(k,model.variables().get(k).type(),value!=null,value));
            next=i+1<keys.size() ? k : null;
            if(!RuntimeJson.utf8Fits(json.variablePage(new WorkflowVariablePage(instanceId,before,items,next)),1024*1024)) {
                items.remove(items.size()-1); RuntimeInputs.require(!items.isEmpty(),Category.LIMIT_EXCEEDED); next=items.get(items.size()-1).key(); break;
            }
        }
        long after=instances.readLockVersion(instanceId).orElseThrow(() -> new WorkflowExecutionException(Category.NOT_FOUND));
        RuntimeInputs.require(before==after,Category.STALE_VERSION);
        return new WorkflowVariablePage(instanceId,before,items,next);
    }
    public Optional<WorkflowInstanceView> findInstance(long id) {
        RuntimeInputs.id(id);
        return RuntimePersistenceErrors.call(() -> instances.findById(id).map(entity -> RuntimeViews.instance(entity, pins.identity(entity))));
    }
    /**
     * Host-authorized monitoring metadata only. Does not load or validate each instance's pinned evidence;
     * callers use findInstance for that detail boundary. No lifecycle/selectability eligibility checks.
     */
    public WorkflowInstancePage listInstances(WorkflowInstanceQuery query) {
        RuntimeInputs.require(query != null, Category.INVALID_INPUT);
        RuntimeInputs.page(query.afterId(), query.limit());
        return RuntimePersistenceErrors.call(() -> page(instances.findSummaryPage(query.status(), query.afterId(),
                PageRequest.of(0, query.limit() + 1)), query.limit(), RuntimeViews::instanceSummary,
                WorkflowInstanceSummary::id, WorkflowInstancePage::new));
    }
    public Optional<WorkflowActivityView> findActivity(long id) {
        RuntimeInputs.id(id);
        return RuntimePersistenceErrors.call(() -> activities.findById(id).map(RuntimeViews::activity));
    }
    public Optional<WorkflowTaskView> findTask(long id) {
        RuntimeInputs.id(id);
        return RuntimePersistenceErrors.call(() -> tasks.findById(id).map(entity -> RuntimeViews.task(entity, owner(entity))));
    }
    /** Pinned policy for an exact task already authorized by the host. */
    public io.github.leontecristian.workflow.core.dto.runtime.TaskClaimPolicy taskClaimPolicy(long instanceId, long taskId) {
        RuntimeInputs.id(instanceId);
        RuntimeInputs.id(taskId);
        return RuntimePersistenceErrors.call(() -> {
            var task = tasks.findById(taskId).orElseThrow(() -> new WorkflowExecutionException(Category.NOT_FOUND));
            var activity = owner(task);
            RuntimeInputs.require(activity.getInstanceId().equals(instanceId), Category.NOT_FOUND);
            var instance = instances.findById(instanceId).orElseThrow(() -> new WorkflowExecutionException(Category.NOT_FOUND));
            var human = pins.load(instance).model().humans().get(activity.getNodeKey());
            RuntimeInputs.require(human != null, Category.HOST_CONFIGURATION);
            return human.claimPolicy();
        });
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
    /** Bounded source scan. Continuation is the last examined candidate, including denied candidates. */
    public WorkflowTaskPage listEligibleTasks(String actorRef, long afterId, int limit,
            java.util.function.Predicate<WorkflowTaskView> hostAccess) {
        RuntimeInputs.require(RuntimeInputs.text(actorRef,200,200,true) && hostAccess != null, Category.INVALID_INPUT);
        RuntimeInputs.page(afterId,limit);
        return RuntimePersistenceErrors.call(() -> {
            var items = new ArrayList<WorkflowTaskView>();
            long cursor = afterId;
            int examined = 0;
            while (examined < 500 && items.size() < limit) {
                int chunk = Math.min(100, 500-examined);
                var rows = tasks.findEligibleCandidatesPage(actorRef, actorRef.length()*2, cursor, PageRequest.of(0,chunk+1));
                int count = Math.min(chunk,rows.size());
                for (int i=0;i<count;i++) {
                    var task = RuntimeViews.task(rows.get(i));
                    if (hostAccess.test(task) && (actorRef.equals(task.assigneeRef()) || eligibility.isEligible(task, actorRef))) {
                        items.add(task);
                        if (!RuntimeJson.utf8Fits(json.encode(new WorkflowTaskPage(items,task.id())),1024*1024)) {
                            items.remove(items.size()-1);
                            RuntimeInputs.require(!items.isEmpty(),Category.LIMIT_EXCEEDED);
                            return new WorkflowTaskPage(items,cursor);
                        }
                    }
                    cursor=task.id(); examined++;
                    if (items.size()==limit || examined==500) {
                        boolean more = i+1<rows.size();
                        return new WorkflowTaskPage(items,more ? cursor : null);
                    }
                }
                if (rows.size()<=chunk) return new WorkflowTaskPage(items,null);
            }
            return new WorkflowTaskPage(items,cursor);
        });
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
            RuntimeInputs.require(RuntimeJson.fits(entity.getDetailsJson(), 4 * 1024 * 1024), Category.LIMIT_EXCEEDED);
            var result=new WorkflowHistoryEvent(RuntimeViews.history(entity), entity.getDetailsJson());
            RuntimeInputs.require(RuntimeJson.utf8Fits(json.encode(result),8*1024*1024),Category.LIMIT_EXCEEDED);
            return result;
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
        RuntimeInputs.require(fact.getDetailsSchemaVersion() == 2, Category.UNSUPPORTED_VERSION);
        var details = json.tree(fact.getDetailsJson());
        RuntimeInputs.require(details.isObject() && details.size() == 3 && details.has("outcomeKey") && details.has("comment") && details.has("inputs")
                && details.get("outcomeKey").isTextual() && details.get("inputs").isObject() && details.get("inputs").isEmpty()
                && (details.get("comment").isNull() || (details.get("comment").isTextual()
                    && RuntimeInputs.text(details.get("comment").textValue(), 2000, 2000, false))), Category.HOST_CONFIGURATION);
        RuntimeInputs.require(task.getStatus() == TaskStatus.COMPLETED && task.getClosureReason() == TaskClosureReason.TASK_OUTCOME
                && fact.getActorKind() == ActorKind.USER && (task.getAssignmentKind() != io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind.USER
                    || Objects.equals(task.getAssignmentRef(), fact.getActorRef()))
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
            RuntimeInputs.require(RuntimeJson.utf8Fits(json.encode(create.apply(List.of(item), cursor.applyAsLong(item))), 1024 * 1024), Category.LIMIT_EXCEEDED);
            emitted.add(item);
            Long next = i + 1 < rows.size() ? cursor.applyAsLong(item) : null;
            P candidate = create.apply(emitted, next);
            if (!RuntimeJson.utf8Fits(json.encode(candidate), 1024 * 1024)) {
                emitted.remove(emitted.size() - 1);
                RuntimeInputs.require(!emitted.isEmpty(), Category.LIMIT_EXCEEDED);
                return create.apply(emitted, cursor.applyAsLong(emitted.get(emitted.size() - 1)));
            }
        }
        Long next = rows.size() > limit ? cursor.applyAsLong(emitted.get(emitted.size() - 1)) : null;
        return create.apply(emitted, next);
    }
}
