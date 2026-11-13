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
import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Human;
import io.github.leontecristian.workflow.core.dto.definition.document.TaskResultContract.Mode;
import io.github.leontecristian.workflow.core.service.definition.DefinitionContentCanonicalizer;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.ActivityStatus;
import io.github.leontecristian.workflow.core.dto.runtime.CompletionPolicy;
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
import java.util.function.Predicate;
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
        var values=variables.read(instanceId,model);
        var visibleKeys=model.variables().keySet().stream()
                .filter(key -> !model.namedOutputs().containsKey(key) || values.containsKey(key)).sorted().toList();
        RuntimeInputs.require(afterKey==null || visibleKeys.contains(afterKey),Category.INVALID_INPUT);
        var keys=visibleKeys.stream().filter(key -> afterKey==null || key.compareTo(afterKey)>0).toList();
        var items=new ArrayList<WorkflowVariableView>();
        String next=null;
        for(int i=0;i<Math.min(keys.size(),limit);i++) {
            String k=keys.get(i); var value=values.get(k);
            items.add(new WorkflowVariableView(k,model.variables().get(k).type(),value!=null,value));
            next=i+1<keys.size() ? k : null;
            if(!RuntimeJson.utf8Fits(json.variablePage(new WorkflowVariablePage(instanceId,before,items,next)),RuntimeJson.LARGE_READ_UTF8_BYTES)) {
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
    /** Creation-time values for an exact task already authorized by the host. */
    public Map<String, WorkflowValue> taskInputs(long instanceId, long taskId) {
        RuntimeInputs.id(instanceId); RuntimeInputs.id(taskId);
        return RuntimePersistenceErrors.call(() -> {
            var task = tasks.findById(taskId).orElseThrow(() -> new WorkflowExecutionException(Category.NOT_FOUND));
            var activity = owner(task);
            RuntimeInputs.require(activity.getInstanceId().equals(instanceId), Category.NOT_FOUND);
            var inputs = pinnedHuman(activity).inputs();
            if (inputs.isEmpty()) return Map.of();
            var fact = history.findCreationByTaskId(taskId)
                    .orElseThrow(() -> new WorkflowExecutionException(Category.HOST_CONFIGURATION));
            return RuntimeTaskCreationEvidence.read(fact, task, activity, inputs, json, history);
        });
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
        return findHistoryEvent(instanceId, sequence, summary -> false);
    }
    /**
     * Host-authorized instance history. Creation snapshots additionally require the host's
     * current exact-task read grant, based on the receiving task and activity in the summary.
     */
    public Optional<WorkflowHistoryEvent> findHistoryEvent(long instanceId, long sequence,
            Predicate<WorkflowHistorySummary> canReadTaskInputs) {
        RuntimeInputs.id(instanceId); RuntimeInputs.id(sequence);
        Objects.requireNonNull(canReadTaskInputs);
        var fact = RuntimePersistenceErrors.call(() -> history.findById(new WorkflowHistoryId(instanceId, sequence)));
        if (fact.isEmpty()) return Optional.empty();
        var entity = fact.get();
        var summary = RuntimePersistenceErrors.call(() -> RuntimeViews.history(entity));
        if ("ACTIVITY_INPUTS_CAPTURED".equals(summary.eventType())) return Optional.empty();
        boolean creation = "TASK_CREATED".equals(summary.eventType());
        boolean protectedInputs = creation && summary.detailsSchemaVersion() == RuntimeTaskCreationEvidence.INPUTS_VERSION;
        if (protectedInputs && (summary.taskId() == null || summary.activityId() == null || !canReadTaskInputs.test(summary)))
            return Optional.empty();
        return RuntimePersistenceErrors.call(() -> {
            if (protectedInputs) {
                var task = tasks.findById(summary.taskId())
                        .orElseThrow(() -> new WorkflowExecutionException(Category.HOST_CONFIGURATION));
                var activity = owner(task);
                RuntimeTaskCreationEvidence.read(entity, task, activity, pinnedHuman(activity).inputs(), json, history);
            } else if (creation) {
                RuntimeInputs.require(!json.tree(entity.getDetailsJson()).has("inputs"), Category.HOST_CONFIGURATION);
            }
            RuntimeInputs.require(entity.getDetailsJson().length()*2L <= RuntimeJson.HISTORY_UTF16_BYTES
                    && RuntimeJson.utf8Fits(entity.getDetailsJson(),RuntimeJson.HISTORY_UTF8_BYTES), Category.LIMIT_EXCEEDED);
            var result=new WorkflowHistoryEvent(summary, entity.getDetailsJson());
            RuntimeInputs.require(RuntimeJson.utf8Fits(json.encode(result),RuntimeJson.HISTORY_RESPONSE_UTF8_BYTES),Category.LIMIT_EXCEEDED);
            return Optional.of(result);
        });
    }
    public Optional<TaskSubmission> findTaskSubmission(long taskId) {
        RuntimeInputs.id(taskId);
        return RuntimePersistenceErrors.call(() -> tasks.findById(taskId).filter(t -> t.getStatus() == TaskStatus.COMPLETED).map(task -> {
            var activity = owner(task);
            return submission(task, activity, submissionMode(activity));
        }));
    }
    public TaskSubmissionPage listActivitySubmissions(long activityId) { return listActivitySubmissions(activityId, 0, 50); }
    public TaskSubmissionPage listActivitySubmissions(long activityId, long afterTaskId, int limit) {
        RuntimeInputs.id(activityId); RuntimeInputs.page(afterTaskId, limit);
        return RuntimePersistenceErrors.call(() -> {
            var rows = tasks.findCompletedPage(activityId, afterTaskId, PageRequest.of(0, limit + 1));
            if (rows.isEmpty()) return new TaskSubmissionPage(List.of(), null);
            var activity = owner(rows.get(0));
            var mode = submissionMode(activity);
            return page(rows, limit, task -> submission(task, activity, mode), TaskSubmission::taskId,
                    TaskSubmissionPage::new, json::submissionPageFits);
        });
    }
    /** Internal service input resolution under the already authorized command and its pinned model. */
    WorkflowJsonValue resolveSubmission(long instanceId, String nodeKey, Mode mode) {
        var matches = activities.findByInstanceIdAndNodeKey(instanceId, nodeKey, PageRequest.of(0, 2));
        RuntimeInputs.require(matches.size() <= 1, Category.HOST_CONFIGURATION);
        RuntimeInputs.require(!matches.isEmpty(), Category.SUBMISSION_UNAVAILABLE);
        var activity = matches.get(0);
        RuntimeInputs.require(activity.getInstanceId().equals(instanceId) && activity.getNodeKey().equals(nodeKey),
                Category.HOST_CONFIGURATION);
        RuntimeInputs.require(activity.getStatus() == ActivityStatus.COMPLETED, Category.SUBMISSION_UNAVAILABLE);
        if (activity.getCompletionPolicy() == CompletionPolicy.ALL) return collectiveSubmission(activity,mode);
        RuntimeInputs.require(activity.getCompletionPolicy() == CompletionPolicy.SINGLE && activity.getRequiredTaskCount() == 1,
                Category.HOST_CONFIGURATION);
        var roster = tasks.findPageByActivityId(activity.getId(), 0, PageRequest.of(0, 2));
        RuntimeInputs.require(roster.size() == 1, Category.HOST_CONFIGURATION);
        var task = roster.get(0);
        var result = submission(task, activity, mode).result();
        RuntimeInputs.require(TaskClosureReason.TASK_OUTCOME.name().equals(activity.getClosureReason())
                && Objects.equals(task.getOutcomeKey(), activity.getOutcomeKey())
                && Objects.equals(task.getClosedBy(), activity.getClosedBy())
                && Objects.equals(task.getClosedAt(), activity.getClosedAt()), Category.HOST_CONFIGURATION);
        RuntimeInputs.require(result != null, Category.SUBMISSION_UNAVAILABLE);
        return result;
    }
    private WorkflowJsonValue collectiveSubmission(WorkflowActivityEntity activity,Mode mode) {
        var roster = tasks.findPageByActivityId(activity.getId(),0,PageRequest.of(0,101));
        var facts = history.findActivityFacts(activity.getId(),"TASK_COMPLETED",PageRequest.of(0,101));
        RuntimeInputs.require(roster.size() == activity.getRequiredTaskCount() && roster.size() >= 1 && roster.size() <= 100
                && TaskClosureReason.TASK_OUTCOME.name().equals(activity.getClosureReason()),Category.HOST_CONFIGURATION);
        var byTask = new java.util.HashMap<Long,io.github.leontecristian.workflow.core.entity.runtime.WorkflowHistoryEntity>();
        for (var fact : facts) RuntimeInputs.require(fact.getTaskId() != null && byTask.putIfAbsent(fact.getTaskId(),fact) == null,Category.HOST_CONFIGURATION);
        var collection = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        int completed = 0;
        var keys = new java.util.HashSet<String>();
        for (var task : roster.stream().sorted(java.util.Comparator.comparing(WorkflowTaskEntity::getTaskKey)).toList()) {
            RuntimeInputs.require(RuntimeInputs.key(task.getTaskKey()) && keys.add(task.getTaskKey()) && task.getActivityId().equals(activity.getId()),Category.HOST_CONFIGURATION);
            if (task.getStatus() != TaskStatus.COMPLETED) {
                RuntimeInputs.require(task.getStatus() == TaskStatus.CANCELLED && task.getClosureReason() == TaskClosureReason.ACTIVITY_RESOLVED
                        && !byTask.containsKey(task.getId()) && task.getOutcomeKey() == null
                        && Objects.equals(task.getClosedAt(),activity.getClosedAt()) && Objects.equals(task.getClosedBy(),activity.getClosedBy()),Category.HOST_CONFIGURATION);
                continue;
            }
            var fact = byTask.get(task.getId());
            RuntimeInputs.require(fact != null,Category.HOST_CONFIGURATION);
            var accepted = submission(task,activity,mode,fact);
            var entry = collection.addObject(); entry.put("taskId",Long.toString(task.getId())); entry.put("taskKey",task.getTaskKey());
            entry.put("outcomeKey",accepted.outcomeKey()); entry.put("comment",accepted.comment());
            if (accepted.result() != null) entry.set("result",json.tree(accepted.result().toJson()));
            completed++;
        }
        RuntimeInputs.require(completed > 0 && completed == facts.size(),Category.HOST_CONFIGURATION);
        var decisive = facts.stream().max(java.util.Comparator.comparing(f -> f.getId().getSequenceNumber())).orElseThrow();
        var decisiveTask = roster.stream().filter(t -> t.getId().equals(decisive.getTaskId())).findFirst().orElseThrow();
        RuntimeInputs.require(Objects.equals(decisiveTask.getOutcomeKey(),activity.getOutcomeKey())
                && Objects.equals(decisive.getActorRef(),activity.getClosedBy()) && Objects.equals(decisive.getOccurredAt(),activity.getClosedAt())
                && (!Objects.equals(activity.getOutcomeKey(),activity.getContributionOutcomeKey()) || completed == roster.size())
                && roster.stream().filter(t -> t.getStatus() == TaskStatus.COMPLETED && !t.getId().equals(decisiveTask.getId()))
                    .allMatch(t -> Objects.equals(t.getOutcomeKey(),activity.getContributionOutcomeKey())),Category.HOST_CONFIGURATION);
        try { return WorkflowJsonValue.parse(json.encode(collection)); }
        catch (WorkflowExecutionException failure) {
            if (failure.category() == Category.LIMIT_EXCEEDED)
                throw WorkflowExecutionException.resultLimit("The original activity submission collection exceeds its capacity.");
            throw failure;
        }
    }
    private WorkflowActivityEntity owner(WorkflowTaskEntity task) {
        return activities.findById(task.getActivityId()).orElseThrow(() -> new WorkflowExecutionException(Category.HOST_CONFIGURATION));
    }
    private Mode submissionMode(WorkflowActivityEntity activity) {
        return pinnedHuman(activity).resultContract().mode();
    }
    private Human pinnedHuman(WorkflowActivityEntity activity) {
        var instance = instances.findById(activity.getInstanceId())
                .orElseThrow(() -> new WorkflowExecutionException(Category.HOST_CONFIGURATION));
        var human = pins.load(instance).model().humans().get(activity.getNodeKey());
        RuntimeInputs.require(human != null, Category.HOST_CONFIGURATION);
        return human;
    }
    private TaskSubmission submission(WorkflowTaskEntity task, WorkflowActivityEntity activity, Mode mode) {
        RuntimeInputs.require(activity.getId().equals(task.getActivityId()), Category.HOST_CONFIGURATION);
        var fact = history.findCompletionByTaskId(task.getId()).orElseThrow(() -> new WorkflowExecutionException(Category.HOST_CONFIGURATION));
        return submission(task,activity,mode,fact);
    }
    private TaskSubmission submission(WorkflowTaskEntity task, WorkflowActivityEntity activity, Mode mode,
            io.github.leontecristian.workflow.core.entity.runtime.WorkflowHistoryEntity fact) {
        short version = fact.getDetailsSchemaVersion();
        RuntimeInputs.require(version == 2 || version == 3, Category.UNSUPPORTED_VERSION);
        var details = json.tree(fact.getDetailsJson());
        RuntimeInputs.require(details.isObject() && details.size() == (version == 3 ? 4 : 3)
                && (version == 2 || details.path("result").isObject()) && details.has("outcomeKey") && details.has("comment") && details.has("inputs")
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
        RuntimeInputs.require(version == 3 ? mode != Mode.FORBIDDEN : mode != Mode.REQUIRED, Category.HOST_CONFIGURATION);
        WorkflowJsonValue result = null;
        if (version == 3) {
            try {
                result = CompleteWorkflowTask.parseResult(
                        DefinitionContentCanonicalizer.canonical(details.get("result")));
            } catch (WorkflowExecutionException failure) {
                throw new WorkflowExecutionException(Category.HOST_CONFIGURATION, failure);
            }
        }
        return new TaskSubmission(task.getId(), activity.getId(), activity.getInstanceId(), fact.getId().getSequenceNumber(),
                fact.getCommandId(), fact.getActorKind(), fact.getActorRef(), RuntimeViews.instant(fact.getOccurredAt()), task.getOutcomeKey(),
                details.get("comment").isNull() ? null : details.get("comment").textValue(), Map.of(), result);
    }

    /** SQL limits rows first. JSON bytes bound the complete page including continuation; no item is split or skipped. */
    private <R,T,P> P page(List<R> rows, int limit, Function<R,T> map, ToLongFunction<T> cursor, BiFunction<List<T>,Long,P> create) {
        return page(rows, limit, map, cursor, create, page -> RuntimeJson.utf8Fits(json.encode(page), 1024 * 1024));
    }
    private <R,T,P> P page(List<R> rows, int limit, Function<R,T> map, ToLongFunction<T> cursor,
            BiFunction<List<T>,Long,P> create, java.util.function.Predicate<P> fits) {
        List<T> emitted = new ArrayList<>();
        for (int i = 0; i < Math.min(rows.size(), limit); i++) {
            T item = map.apply(rows.get(i));
            // A single item must fit its own complete page, even if this page is already nearly full.
            RuntimeInputs.require(fits.test(create.apply(List.of(item), cursor.applyAsLong(item))), Category.LIMIT_EXCEEDED);
            emitted.add(item);
            Long next = i + 1 < rows.size() ? cursor.applyAsLong(item) : null;
            P candidate = create.apply(emitted, next);
            if (!fits.test(candidate)) {
                emitted.remove(emitted.size() - 1);
                RuntimeInputs.require(!emitted.isEmpty(), Category.LIMIT_EXCEEDED);
                return create.apply(emitted, cursor.applyAsLong(emitted.get(emitted.size() - 1)));
            }
        }
        Long next = rows.size() > limit ? cursor.applyAsLong(emitted.get(emitted.size() - 1)) : null;
        return create.apply(emitted, next);
    }
}
