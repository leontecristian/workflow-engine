package io.github.leontecristian.workflow.core.service.runtime;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
import io.github.leontecristian.workflow.core.dto.runtime.CancelWorkflow;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCancellationResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCancellationCommandResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCancellationContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCancellationAuthorizationContext;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionContext;
import io.github.leontecristian.workflow.core.dto.runtime.StartInputs;

import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.AdministrativelyReleaseWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.ClaimWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.ReleaseWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.TaskClaimPolicy;
import io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext;
import io.github.leontecristian.workflow.core.dto.runtime.ActivityStatus;
import io.github.leontecristian.workflow.core.dto.runtime.CompletionPolicy;
import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.StartWorkflow;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowExecutionResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowOperation;
import io.github.leontecristian.workflow.core.dto.runtime.InstanceStatus;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.AllocatedWorkflowInstance;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentContext;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowInstanceEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowActivityEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowTaskEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowCommandReceiptEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowHistoryId;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowInstanceRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowActivityRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowTaskRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowHistoryRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowCommandReceiptRepository;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionRuntimeReader;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import io.github.leontecristian.workflow.core.dto.definition.document.ExecutableDefinition;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import io.github.leontecristian.workflow.core.dto.definition.document.TaskResultContract;
import io.github.leontecristian.workflow.core.dto.runtime.TaskSubmissionValidationContext;
import io.github.leontecristian.workflow.core.service.definition.TaskResultSchema;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.ObjectProvider;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trusted host-authorized commands. Mutations require the existing host transaction; all returned
 * results are provisional until its successful commit. Links, resolver and durable recorder must not
 * commit independently or emit remote effects. Runtime adapters are required, never synthesized.
 *
 * <p>After STALE_VERSION/CONCURRENT_COMMAND finish rollback. The host may reauthorize and invoke
 * findReplay with the original command/token in a fresh transaction. Never retry mutations or query
 * a failed transaction. Receipt replay returns the original snapshot, not current state.
 */
@Service
@Transactional(readOnly = true)
public class WorkflowExecutionService {
    private final WorkflowInstanceRepository instances;
    private final WorkflowActivityRepository activities;
    private final WorkflowTaskRepository tasks;
    private final WorkflowHistoryRepository history;
    private final WorkflowCommandReceiptRepository receipts;
    private final EntityManager entityManager;
    private final WorkflowDefinitionRuntimeReader definitions;
    private final TaskTypeCatalogService catalog;
    private final RuntimeJson json;
    private final RuntimeHostSupport host;
    private final WorkflowTaskEligibilityService eligibility;
    private final RuntimeDefinitionPins pins;
    private final RuntimeVariables variables;
    private final RuntimeServiceProgression services;
    private final ObjectProvider<WorkflowTaskSubmissionValidator> submissionValidator;
    private final ObjectProvider<WorkflowCancellationAuthorization> cancellationAuthorization;
    private ObjectProvider<WorkflowTaskReassignmentAuthorization> reassignmentAuthorization;

    @org.springframework.beans.factory.annotation.Autowired
    public void setReassignmentAuthorization(ObjectProvider<WorkflowTaskReassignmentAuthorization> authorization) {
        this.reassignmentAuthorization = authorization;
    }

    private final ThreadLocal<Boolean> executing = new ThreadLocal<>();

    public WorkflowExecutionService(WorkflowInstanceRepository instances, WorkflowActivityRepository activities,
                                    WorkflowTaskRepository tasks, WorkflowHistoryRepository history, WorkflowCommandReceiptRepository receipts,
                                    EntityManager entityManager, WorkflowDefinitionRuntimeReader definitions, TaskTypeCatalogService catalog,
                                    RuntimeJson json, RuntimeHostSupport host, RuntimeDefinitionPins pins, RuntimeVariables variables, RuntimeServiceProgression services, WorkflowTaskEligibilityService eligibility,
                                    ObjectProvider<WorkflowTaskSubmissionValidator> submissionValidator) {
        this(instances, activities, tasks, history, receipts, entityManager, definitions, catalog, json, host,
                pins, variables, services, eligibility, submissionValidator, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public WorkflowExecutionService(WorkflowInstanceRepository instances, WorkflowActivityRepository activities,
            WorkflowTaskRepository tasks, WorkflowHistoryRepository history, WorkflowCommandReceiptRepository receipts,
            EntityManager entityManager, WorkflowDefinitionRuntimeReader definitions, TaskTypeCatalogService catalog,
            RuntimeJson json, RuntimeHostSupport host, RuntimeDefinitionPins pins, RuntimeVariables variables,
            RuntimeServiceProgression services, WorkflowTaskEligibilityService eligibility,
            ObjectProvider<WorkflowTaskSubmissionValidator> submissionValidator,
            ObjectProvider<WorkflowCancellationAuthorization> cancellationAuthorization) {
        this.cancellationAuthorization = cancellationAuthorization;
        this.submissionValidator = submissionValidator;
        this.eligibility = eligibility;
        this.variables = variables;
        this.services = services;
        this.instances = instances;
        this.activities = activities;
        this.tasks = tasks;
        this.history = history;
        this.receipts = receipts;
        this.entityManager = entityManager;
        this.definitions = definitions;
        this.catalog = catalog;
        this.json = json;
        this.host = host;
        this.pins = pins;
    }

    private void validateCancellation(CancelWorkflow command, WorkflowCommandContext context) {
        RuntimeInputs.validateContextAndGetOccurredAtUtc(context);
        RuntimeInputs.require(command != null && command.commandId() != null && command.instanceId() > 0
                && command.expectedLockVersion() >= 0 && RuntimeInputs.isValidText(command.reason(), 2000, 2000, true)
                && command.reason().length() <= 2000 && context.actorKind() == ActorKind.USER, Category.INVALID_INPUT);
    }

    private WorkflowCancellationAuthorization cancellationAdapter() {
        WorkflowCancellationAuthorization adapter;
        try { adapter = cancellationAuthorization == null ? null : cancellationAuthorization.getIfAvailable(); }
        catch (RuntimeException failure) { throw new WorkflowExecutionException(Category.HOST_CONFIGURATION, failure); }
        RuntimeInputs.require(adapter != null, Category.HOST_CONFIGURATION);
        return adapter;
    }

    private WorkflowCancellationAuthorizationContext cancellationAccess(WorkflowInstanceEntity instance,
            WorkflowCommandContext context, WorkflowCancellationAuthorization adapter) {
        var authorization = new WorkflowCancellationAuthorizationContext(instance.getId(), instance.getDefinitionVersionId(),
                instance.getBusinessType(), instance.getBusinessId(), context);
        boolean accessible;
        try { accessible = adapter.canAccess(authorization); }
        catch (RuntimeException failure) { throw new WorkflowExecutionException(Category.AUTHORIZATION_LOOKUP_FAILED, failure); }
        RuntimeInputs.require(accessible, Category.NOT_FOUND);
        return authorization;
    }

    /** Lookup only: business access remains required, fresh cancellation privilege does not. */
    public Optional<WorkflowCancellationResult> findCancellationReplay(CancelWorkflow command, WorkflowCommandContext context) {
        validateCancellation(command, context);
        return RuntimePersistenceErrors.call(() -> {
            var adapter = cancellationAdapter();
            var instance = instances.findById(command.instanceId())
                    .orElseThrow(() -> new WorkflowExecutionException(Category.NOT_FOUND));
            cancellationAccess(instance, context, adapter);
            return json.cancellationReplay(receipts.findById(command.commandId()).orElse(null), command, context);
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public WorkflowCancellationCommandResult cancelWorkflow(CancelWorkflow command, WorkflowCommandContext context) {
        return cancelWorkflow(command, context, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public WorkflowCancellationCommandResult cancelWorkflow(CancelWorkflow command, WorkflowCommandContext context,
            WorkflowCancellationEffect effect) {
        return guarded(() -> {
            validateCancellation(command, context);
            var adapter = cancellationAdapter();
            var instance = instances.findById(command.instanceId())
                    .orElseThrow(() -> new WorkflowExecutionException(Category.NOT_FOUND));
            entityManager.refresh(instance);
            var authorization = cancellationAccess(instance, context, adapter);
            var replay = json.cancellationReplay(receipts.findById(command.commandId()).orElse(null), command, context);
            if (replay.isPresent()) return new WorkflowCancellationCommandResult(true, replay.get());
            boolean permitted;
            try { permitted = adapter.canCancel(authorization); }
            catch (RuntimeException failure) { throw new WorkflowExecutionException(Category.AUTHORIZATION_LOOKUP_FAILED, failure); }
            RuntimeInputs.require(permitted, Category.CANCELLATION_FORBIDDEN);
            RuntimeInputs.require(instance.getLockVersion() == command.expectedLockVersion(), Category.STALE_VERSION);
            RuntimeInputs.require(instance.getStatus() == InstanceStatus.ACTIVE, Category.INVALID_STATE);
            RuntimeInputs.require(instance.getLastHistorySequence() >= 4 && instance.getResultKey() == null
                    && instance.getEndedAt() == null && instance.getEndedBy() == null && instance.getTerminationReason() == null,
                    Category.HOST_CONFIGURATION);
            long first = instance.reserveHistorySequence();
            entityManager.flush(); // Real version-checked aggregate claim precedes children and host effects.
            var activity = activities.findActiveByInstanceId(instance.getId())
                    .orElseThrow(() -> new WorkflowExecutionException(Category.HOST_CONFIGURATION));
            entityManager.refresh(activity);
            var roster = tasks.findPageByActivityId(activity.getId(), 0, PageRequest.of(0, 101));
            roster.forEach(entityManager::refresh);
            RuntimeInputs.require(activity.getStatus() == ActivityStatus.ACTIVE
                    && instance.getCurrentNodeKey().equals(activity.getNodeKey())
                    && activity.getOutcomeKey() == null && activity.getClosedAt() == null && activity.getClosedBy() == null
                    && activity.getClosureReason() == null && activity.getActivationSequence() < first
                    && roster.size() >= 1 && roster.size() <= 100 && roster.size() == activity.getRequiredTaskCount()
                    && (activity.getCompletionPolicy() == CompletionPolicy.SINGLE ? roster.size() == 1
                        : activity.getCompletionPolicy() == CompletionPolicy.ALL)
                    && roster.stream().map(WorkflowTaskEntity::getTaskKey).distinct().count() == roster.size(), Category.HOST_CONFIGURATION);
            var time = RuntimeInputs.validateContextAndGetOccurredAtUtc(context);
            RuntimeInputs.require(!time.isBefore(activity.getEnteredAt()), Category.INVALID_INPUT);
            for (var task : roster) {
                RuntimeInputs.require(RuntimeInputs.isValidKey(task.getTaskKey()) && task.getActivityId().equals(activity.getId())
                        && (task.getStatus() == TaskStatus.OPEN || task.getStatus() == TaskStatus.COMPLETED), Category.HOST_CONFIGURATION);
                if (task.getStatus() == TaskStatus.OPEN) {
                    RuntimeInputs.require(task.getOutcomeKey() == null && task.getClosedAt() == null
                            && task.getClosedBy() == null && task.getClosureReason() == null, Category.HOST_CONFIGURATION);
                } else {
                    RuntimeInputs.require(activity.getCompletionPolicy() == CompletionPolicy.ALL
                            && java.util.Objects.equals(task.getOutcomeKey(), activity.getContributionOutcomeKey())
                            && task.getClosureReason() == io.github.leontecristian.workflow.core.dto.runtime.TaskClosureReason.TASK_OUTCOME
                            && task.getClosedAt() != null && task.getClosedBy() != null, Category.HOST_CONFIGURATION);
                    RuntimeInputs.require(!time.isBefore(task.getClosedAt()), Category.INVALID_INPUT);
                }
                RuntimeInputs.require(!time.isBefore(task.getCreatedAt()), Category.INVALID_INPUT);
            }
            var open = roster.stream().filter(t -> t.getStatus() == TaskStatus.OPEN)
                    .sorted(java.util.Comparator.comparing(WorkflowTaskEntity::getTaskKey)).toList();
            RuntimeInputs.require(!open.isEmpty(), Category.HOST_CONFIGURATION);
            var entered = history.findById(new WorkflowHistoryId(instance.getId(), activity.getActivationSequence()))
                    .orElseThrow(() -> new WorkflowExecutionException(Category.HOST_CONFIGURATION));
            RuntimeInputs.require("ACTIVITY_ENTERED".equals(entered.getEventType())
                    && activity.getId().equals(entered.getActivityId()) && entered.getTaskId() == null
                    && activity.getEnteredAt().equals(entered.getOccurredAt()), Category.HOST_CONFIGURATION);
            var accepted = history.findActivityFacts(activity.getId(), "TASK_COMPLETED", PageRequest.of(0, 101));
            RuntimeInputs.require(accepted.size() == roster.size() - open.size()
                    && accepted.stream().map(f -> f.getTaskId()).distinct().count() == accepted.size(), Category.HOST_CONFIGURATION);
            for (var fact : accepted) {
                var task = roster.stream().filter(t -> t.getId().equals(fact.getTaskId())).findFirst()
                        .orElseThrow(() -> new WorkflowExecutionException(Category.HOST_CONFIGURATION));
                RuntimeInputs.require(task.getStatus() == TaskStatus.COMPLETED
                        && instance.getId().equals(fact.getId().getInstanceId())
                        && fact.getId().getSequenceNumber() > activity.getActivationSequence()
                        && fact.getId().getSequenceNumber() < first && fact.getActorKind() == ActorKind.USER
                        && task.getClosedAt().equals(fact.getOccurredAt()) && task.getClosedBy().equals(fact.getActorRef()),
                        Category.HOST_CONFIGURATION);
                var details = json.tree(fact.getDetailsJson());
                RuntimeInputs.require(task.getOutcomeKey().equals(details.path("outcomeKey").asText()), Category.HOST_CONFIGURATION);
            }
            RuntimeProgression.advance(first, open.size() + 1);
            var definition = pins.load(instance); // No capability lookup or progression on stop.
            var human = definition.model().humans().get(activity.getNodeKey());
            RuntimeInputs.require(human != null && human.completionPolicy() == activity.getCompletionPolicy()
                    && java.util.Objects.equals(human.contributionOutcome(), activity.getContributionOutcomeKey()), Category.HOST_CONFIGURATION);
            var before = new WorkflowCancellationContext(command, context, RuntimeViews.instance(instance, definition), RuntimeViews.activity(activity));
            if (effect != null) {
                try { effect.apply(before); }
                catch (RuntimeException failure) { throw new WorkflowExecutionException(Category.CANCELLATION_EFFECT_FAILED, failure); }
            }
            var recorder = host.recorder();
            var batch = new RuntimeHistoryBatch(entityManager, json, instance.getId(), instance.getDefinitionVersionId(), command.commandId(), context);
            batch.beginAt(first - 1);
            for (var task : open) {
                task.cancelForInstance(time, context.actorRef());
                var details = Map.of("taskKey", task.getTaskKey(), "closureReason", "INSTANCE_CANCELLED");
                batch.next("TASK_CANCELLED", activity.getId(), task.getId(), details, details, (short) 1);
            }
            activity.cancel(time, context.actorRef());
            var closure = Map.of("closureReason", "INSTANCE_CANCELLED");
            batch.next("ACTIVITY_CANCELLED", activity.getId(), null, closure, closure, (short) 1);
            batch.next("INSTANCE_CANCELLED", null, null, Map.of("reason", command.reason()),
                    Map.of("cancelledTaskCount", Integer.toString(open.size())), (short) 1);
            instance.cancel(command.reason(), batch.last(), time, context.actorRef());
            entityManager.flush();
            var result = new WorkflowCancellationResult(command.commandId(), WorkflowOperation.CANCEL_WORKFLOW,
                    RuntimeViews.instance(instance, definition), RuntimeViews.activity(activity), open.size(), first, batch.last());
            entityManager.persist(new WorkflowCommandReceiptEntity(command.commandId(), instance.getId(), WorkflowOperation.CANCEL_WORKFLOW.name(),
                    context.actorKind(), context.actorRef(), json.fingerprint(command, context), RuntimeJson.FINGERPRINT_VERSION,
                    time, json.evidence(result), (short) 7));
            entityManager.flush();
            host.record(recorder, batch.events());
            entityManager.flush();
            RuntimeInputs.require(result.instance().equals(RuntimeViews.instance(instance, definition))
                    && result.activity().equals(RuntimeViews.activity(activity)), Category.HOST_CONFIGURATION);
            return new WorkflowCancellationCommandResult(false, result);
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public WorkflowCommandResult start(StartWorkflow command, WorkflowCommandContext context) {
        return start(command, context, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public WorkflowCommandResult start(StartWorkflow command, WorkflowCommandContext context, StartLinkAction links) {
        return start(command, StartInputs.empty(), context, links);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public WorkflowCommandResult start(StartWorkflow command, StartInputs inputs, WorkflowCommandContext context, StartLinkAction links) {
        return guarded(() -> {
            RuntimeInputs.validateStartCommand(command, context);
            RuntimeValueBindings.basic(inputs,json);
            RuntimeInputs.require((command.hostStartIntent() == null) == (links == null), Category.INVALID_INPUT);
            byte[] fingerprint = json.fingerprint(command, inputs, context);
            var replay = startReplay(command, context, fingerprint);
            if (replay.isPresent()) return new WorkflowCommandResult(true, replay.get());
            var recorder = host.recorder();
            var definition = definitions.loadForStart(command);
            definitions.requireCapabilities(definition, true);
            var model = definition.model();
            var acceptedInputs=RuntimeValueBindings.inputs(model,inputs,json);
            var time = RuntimeInputs.validateContextAndGetOccurredAtUtc(context);
            var progression = new RuntimeProgression();
            var instance = new WorkflowInstanceEntity(definition.versionId(), command.businessType(), command.businessId(),
                    model.start().key(), time, context.actorRef());
            entityManager.persist(instance); // IDENTITY allocates the ID before required host links.
            if (links != null) {
                try { links.link(new AllocatedWorkflowInstance(command.commandId(), instance.getId(), definition.versionId(),
                        command.businessType(), command.businessId(), context)); }
                catch (RuntimeException failure) { throw RuntimePersistenceErrors.translate(failure); }
            }
            progression.visit(model.start(), "START");
            RuntimeHistoryBatch batch = new RuntimeHistoryBatch(entityManager, json, instance.getId(), definition.versionId(), command.commandId(), context);
            var started = json.object(); started.put("definitionVersionId", Long.toString(definition.versionId()));
            started.put("definitionKey", definition.key()); started.put("definitionVersion", definition.version());
            started.put("definitionHash", definition.contentHash()); started.put("businessType", command.businessType()); started.put("businessId", command.businessId());
            started.set("hostStartIntent", json.tree(json.evidence(command.hostStartIntent())));
            batch.next("INSTANCE_STARTED",null,null,null,started,Map.of());
            Map<String,WorkflowValue> state=new java.util.TreeMap<>();
            if (model.extended()) {
                batch.next("START_INPUTS_ACCEPTED",null,null,null,Map.of("inputs",RuntimeValueBindings.tokens(acceptedInputs,json)),Map.of());
                var writes=RuntimeValueBindings.prepare(model.initialize(),model.variables(),state,acceptedInputs,Category.INVALID_INPUT);
                variables.apply(instance.getId(),model.start().key(),model.initialize(),state,writes,batch);
            }
            var submissions = new java.util.HashMap<String,io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue>();
            var paused=services.traverse(model,model.start().key(),null,new ServiceActionContext(
                    command.commandId(),instance.getId(),definition.versionId(),model.start().key(),command.businessType(),command.businessId(),context),state,progression,batch,definition.contentHash(),null,submissions);
            RuntimeInputs.require("HUMAN_TASK".equals(paused.type()),Category.HOST_CONFIGURATION);
            var stage = enterStage(definition,instance,paused.key(),command.commandId(),context,batch,state,submissions);
            instance.waitAt(paused.key(),batch.last());
            return finish(command.commandId(), WorkflowOperation.START_WORKFLOW, context, fingerprint, definition, instance, stage.activity(), stage.task(), 1, batch, recorder, json.fingerprintVersion(command));
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public WorkflowCommandResult completeTask(CompleteWorkflowTask command, WorkflowCommandContext context) {
        return guarded(() -> {
            RuntimeInputs.validateCompleteTaskCommand(command, context);
            byte[] fingerprint = json.fingerprint(command, context);
            var replay = completionReplay(command, context, fingerprint);
            if (replay.isPresent()) return new WorkflowCommandResult(true, replay.get());
            RuntimeInputs.require(context.actorKind() == ActorKind.USER, Category.NOT_ASSIGNED);
            var recorder = host.recorder();
            var instance = instances.findById(command.instanceId()).orElseThrow(() -> new WorkflowExecutionException(Category.NOT_FOUND));
            entityManager.refresh(instance);
            RuntimeInputs.require(instance.getLockVersion() == command.expectedLockVersion(), Category.STALE_VERSION);
            RuntimeInputs.require(instance.getStatus() == InstanceStatus.ACTIVE && instance.getLastHistorySequence() >= 4, Category.INVALID_STATE);
            RuntimeProgression.advance(instance.getLastHistorySequence(), 4);
            long first = instance.reserveHistorySequence();
            entityManager.flush(); // Early real @Version write; no child writes, callback or recorder before this succeeds.

            var activity = activities.findActiveByInstanceId(instance.getId()).orElseThrow(() -> new WorkflowExecutionException(Category.HOST_CONFIGURATION));
            var task = tasks.findById(command.taskId()).orElseThrow(() -> new WorkflowExecutionException(Category.NOT_FOUND));
            entityManager.refresh(activity); entityManager.refresh(task);
            RuntimeInputs.require(task.getActivityId().equals(activity.getId()) && activity.getInstanceId().equals(instance.getId()), Category.INVALID_STATE);
            RuntimeInputs.require(activity.getStatus() == ActivityStatus.ACTIVE && task.getStatus() == TaskStatus.OPEN, Category.INVALID_STATE);
            var definition = pins.load(instance);
            definitions.requireCapabilities(definition, false);
            var model = definition.model();
            var human = model.humans().get(activity.getNodeKey());
            RuntimeInputs.require(human != null,Category.HOST_CONFIGURATION);
            var taskType = catalog.findByCode(human.taskType()).orElseThrow(() -> new WorkflowExecutionException(Category.HOST_CONFIGURATION));
            RuntimeInputs.require(instance.getCurrentNodeKey().equals(human.key()) && activity.getNodeKey().equals(human.key())
                    && activity.getTaskTypeId() == taskType.id() && activity.getCompletionPolicy() == human.completionPolicy()
                    && activity.getRequiredTaskCount() >= 1 && activity.getRequiredTaskCount() <= 100
                    && activity.getActivationSequence() > 0 && activity.getActivationSequence() < first
                    && java.util.Objects.equals(activity.getContributionOutcomeKey(),human.contributionOutcome()), Category.HOST_CONFIGURATION);
            var roster = tasks.findPageByActivityId(activity.getId(), 0, PageRequest.of(0, 101));
            for (var sibling : roster) if (!sibling.getId().equals(task.getId())) entityManager.refresh(sibling);
            RuntimeInputs.require(roster.size() == activity.getRequiredTaskCount()
                    && (activity.getCompletionPolicy() != CompletionPolicy.SINGLE || roster.size() == 1)
                    && roster.stream().anyMatch(t -> t.getId().equals(task.getId()))
                    && roster.stream().allMatch(t -> t.getStatus() == TaskStatus.OPEN || t.getStatus() == TaskStatus.COMPLETED), Category.HOST_CONFIGURATION);
            RuntimeInputs.require(history.findCompletionByTaskId(task.getId()).isEmpty(), Category.HOST_CONFIGURATION);
            var created = history.findCreationByTaskId(task.getId())
                    .orElseThrow(() -> new WorkflowExecutionException(Category.HOST_CONFIGURATION));
            RuntimeTaskCreationEvidence.read(created, task, activity, human.inputs(), json, history);
            var entered = history.findById(new WorkflowHistoryId(instance.getId(), activity.getActivationSequence()))
                    .orElseThrow(() -> new WorkflowExecutionException(Category.HOST_CONFIGURATION));
            RuntimeInputs.require("ACTIVITY_ENTERED".equals(entered.getEventType()) && activity.getId().equals(entered.getActivityId())
                    && entered.getTaskId() == null && entered.getCommandId().equals(created.getCommandId())
                    && entered.getOccurredAt().equals(activity.getEnteredAt())
                    && entered.getActorKind() == created.getActorKind() && entered.getActorRef().equals(created.getActorRef()), Category.HOST_CONFIGURATION);
            var selectedOutcome = human.outcome(command.outcomeKey())
                    .orElseThrow(() -> new WorkflowExecutionException(Category.INVALID_INPUT));
            RuntimeInputs.require(!selectedOutcome.requiresComment() || (command.comment() != null && !command.comment().isBlank()), Category.INVALID_INPUT);
            var time = RuntimeInputs.validateContextAndGetOccurredAtUtc(context);
            RuntimeInputs.require(!time.isBefore(instance.getStartedAt()) && !time.isBefore(activity.getEnteredAt()) && !time.isBefore(task.getCreatedAt()), Category.INVALID_INPUT);
            RuntimeInputs.require(eligibility.isEligible(RuntimeViews.task(task, activity), context.actorRef()), Category.NOT_ASSIGNED);
            if (task.getAssigneeRef() != null) {
                RuntimeInputs.require(task.getAssigneeRef().equals(context.actorRef()), Category.TASK_ASSIGNED_TO_OTHER);
            } else {
                RuntimeInputs.require(task.getAssignmentKind() != AssignmentTargetKind.USER, Category.HOST_CONFIGURATION);
                RuntimeInputs.require(human.claimPolicy() != TaskClaimPolicy.REQUIRED, Category.CLAIM_REQUIRED);
            }
            validateSubmission(command, new TaskSubmissionValidationContext(context, instance.getId(), definition.versionId(),
                    activity.getId(), task.getId(), human.key(), human.taskType(), command.outcomeKey(), command.comment()),
                    human.resultContract());
            task.complete(command.outcomeKey(), time, context.actorRef());
            RuntimeHistoryBatch batch = new RuntimeHistoryBatch(entityManager, json, instance.getId(), definition.versionId(), command.commandId(), context);
            var submitted = json.object(); submitted.put("outcomeKey", command.outcomeKey()); submitted.put("comment", command.comment()); submitted.putObject("inputs");
            if (command.result() != null) submitted.set("result", json.tree(command.result().toJson()));
            batch.beginAt(first-1);
            batch.append(first, "TASK_COMPLETED", activity.getId(), task.getId(), null, submitted,
                    Map.of("outcomeKey", command.outcomeKey()), (short) (command.result() == null ? 2 : 3));
            boolean resolved = activity.getCompletionPolicy() == CompletionPolicy.SINGLE
                    || !command.outcomeKey().equals(activity.getContributionOutcomeKey())
                    || roster.stream().noneMatch(t -> t.getStatus() == TaskStatus.OPEN);
            if (!resolved) {
                // The early instance claim already advanced the sole contribution history sequence.
                return finish(command.commandId(),WorkflowOperation.COMPLETE_TASK,context,fingerprint,definition,instance,
                        activity,task,first,batch,recorder,json.fingerprintVersion(command));
            }
            for (var sibling : roster.stream().sorted(java.util.Comparator.comparing(WorkflowTaskEntity::getTaskKey)).toList()) {
                if (sibling.getStatus() != TaskStatus.OPEN) continue;
                sibling.cancelForResolution(time,context.actorRef());
                batch.next("TASK_CANCELLED",activity.getId(),sibling.getId(),
                        Map.of("taskKey",sibling.getTaskKey(),"closureReason","ACTIVITY_RESOLVED"),
                        Map.of("taskKey",sibling.getTaskKey(),"closureReason","ACTIVITY_RESOLVED"),(short)1);
            }
            activity.complete(command.outcomeKey(),time,context.actorRef());
            batch.next("ACTIVITY_COMPLETED", activity.getId(), null, null,
                    Map.of("outcomeKey", command.outcomeKey(), "closureReason", "TASK_OUTCOME"), Map.of("outcomeKey", command.outcomeKey()));
            entityManager.flush(); // Final accepted evidence must be query-visible before original collection bindings.
            var submissions = new java.util.HashMap<String,io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue>();
            var output = command.result();
            if (activity.getCompletionPolicy() == CompletionPolicy.ALL && human.outputVariable() != null)
                output = services.resolveSubmission(instance.getId(),human.key(),human.resultContract().mode());
            if (activity.getCompletionPolicy() == CompletionPolicy.ALL && human.outputVariable() != null && output != null) submissions.put(human.key(),output);
            var state = variables.afterTaskResolution(instance.getId(), model, human, output,
                    activity.getId(), activity.getCompletionPolicy() == CompletionPolicy.ALL ? null : task.getId(), batch);
            if (human.resultContract().mode() != TaskResultContract.Mode.FORBIDDEN) {
                entityManager.flush(); // Publish completion evidence and the named copy to same-transaction reads.
            }
            var budget=new RuntimeProgression();
            var end=services.traverse(model,human.key(),null,new ServiceActionContext(
                    command.commandId(),instance.getId(),definition.versionId(),human.key(),instance.getBusinessType(),instance.getBusinessId(),context,activity.getId()),
                    state,budget,batch,definition.contentHash(),selectedOutcome.key(),submissions);
            if ("HUMAN_TASK".equals(end.type())) {
                entityManager.flush(); // Close the previous active row before inserting its successor.
                enterStage(definition,instance,end.key(),command.commandId(),context,batch,state,submissions);
                instance.advanceToHuman(end.key(),batch.last());
            } else {
                budget.visit(end,"END");
                batch.next("INSTANCE_COMPLETED",null,null,null,Map.of("resultKey",end.result()),Map.of("resultKey",end.result()));
                instance.complete(end.key(),end.result(),batch.last(),time,context.actorRef());
            }
            return finish(command.commandId(), WorkflowOperation.COMPLETE_TASK, context, fingerprint, definition, instance, activity, task, first, batch, recorder, json.fingerprintVersion(command));
        });
    }

    private void validateSubmission(CompleteWorkflowTask command, TaskSubmissionValidationContext context,
            TaskResultContract contract) {
        var result = command.result();
        if (contract.mode() == TaskResultContract.Mode.FORBIDDEN) {
            RuntimeInputs.require(result == null, Category.INVALID_INPUT);
            return;
        }
        RuntimeInputs.require(result != null || contract.mode() == TaskResultContract.Mode.OPTIONAL, Category.INVALID_INPUT);
        if (result != null) {
            try {
                var violation = TaskResultSchema.validate(contract, result);
                if (violation != null) {
                    throw WorkflowExecutionException.invalidResultSchema(violation.path(), violation.schemaPath());
                }
            } catch (TaskResultSchema.LimitExceeded failure) {
                throw WorkflowExecutionException.resultLimit(failure.getMessage());
            }
        }
        var validator = submissionValidator.getIfAvailable();
        if (validator == null) return;
        try {
            validator.validate(context, result);
        } catch (RuntimeException failure) {
            if (failure instanceof WorkflowExecutionException rejection && rejection.category() == Category.RESULT_REJECTED) {
                var diagnostic = rejection.diagnostic();
                throw WorkflowExecutionException.rejectResult(diagnostic == null ? "" : diagnostic.path());
            }
            throw new WorkflowExecutionException(Category.RESULT_VALIDATION_FAILED, failure);
        }
    }

    public Optional<WorkflowCommandResult> findReplay(StartWorkflow command, WorkflowCommandContext context) {
        return findReplay(command,StartInputs.empty(),context);
    }
    public Optional<WorkflowCommandResult> findReplay(StartWorkflow command, StartInputs inputs, WorkflowCommandContext context) {
        RuntimeInputs.validateStartCommand(command,context); RuntimeValueBindings.basic(inputs,json);
        return RuntimePersistenceErrors.call(() -> startReplay(command, context, json.fingerprint(command, inputs, context))
                .map(result -> new WorkflowCommandResult(true, result)));
    }

    public Optional<WorkflowCommandResult> findReplay(CompleteWorkflowTask command, WorkflowCommandContext context) {
        RuntimeInputs.validateCompleteTaskCommand(command, context);
        return RuntimePersistenceErrors.call(() -> completionReplay(command, context, json.fingerprint(command, context))
                .map(result -> new WorkflowCommandResult(true, result)));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public WorkflowCommandResult claimTask(ClaimWorkflowTask command, WorkflowCommandContext context) {
        return changeOwnership(ownershipRequest(command), context);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public WorkflowCommandResult releaseTask(ReleaseWorkflowTask command, WorkflowCommandContext context) {
        return changeOwnership(ownershipRequest(command), context);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public WorkflowCommandResult administrativelyReleaseTask(AdministrativelyReleaseWorkflowTask command,
            WorkflowCommandContext context) {
        return changeOwnership(ownershipRequest(command), context);
    }

    public Optional<WorkflowExecutionResult> findReplay(ClaimWorkflowTask command, WorkflowCommandContext context) {
        return findOwnershipReplay(ownershipRequest(command), context);
    }

    public Optional<WorkflowExecutionResult> findReplay(ReleaseWorkflowTask command, WorkflowCommandContext context) {
        return findOwnershipReplay(ownershipRequest(command), context);
    }

    public Optional<WorkflowExecutionResult> findReplay(AdministrativelyReleaseWorkflowTask command, WorkflowCommandContext context) {
        return findOwnershipReplay(ownershipRequest(command), context);
    }

    private WorkflowTaskReassignmentAuthorization reassignmentAdapter() {
        try {
            var adapter = reassignmentAuthorization == null ? null : reassignmentAuthorization.getIfAvailable();
            RuntimeInputs.require(adapter != null, Category.HOST_CONFIGURATION);
            return adapter;
        } catch (WorkflowExecutionException failure) { throw failure; }
        catch (RuntimeException failure) { throw new WorkflowExecutionException(Category.HOST_CONFIGURATION, failure); }
    }

    private void validateReassignment(io.github.leontecristian.workflow.core.dto.runtime.ReassignWorkflowTask command,
            WorkflowCommandContext context) {
        RuntimeInputs.validateContextAndGetOccurredAtUtc(context);
        RuntimeInputs.require(command != null && command.commandId() != null && command.instanceId() > 0
                && command.taskId() > 0 && command.expectedLockVersion() >= 0 && command.assignment() != null
                && command.assignment().kind() != null && RuntimeInputs.isValidText(command.assignment().ref(), 200, 200, true)
                && RuntimeInputs.isValidText(command.reason(), 1000, 1000, true)
                && context.actorKind() == ActorKind.USER, Category.INVALID_INPUT);
    }

    private boolean reassignmentDecision(java.util.function.BooleanSupplier decision) {
        try { return decision.getAsBoolean(); }
        catch (RuntimeException failure) { throw new WorkflowExecutionException(Category.AUTHORIZATION_LOOKUP_FAILED, failure); }
    }

    private EnteredStage reassignmentAccess(io.github.leontecristian.workflow.core.dto.runtime.ReassignWorkflowTask command,
            WorkflowCommandContext context, WorkflowTaskReassignmentAuthorization adapter) {
        var task = tasks.findById(command.taskId()).orElseThrow(() -> new WorkflowExecutionException(Category.NOT_FOUND));
        var activity = activities.findById(task.getActivityId()).orElseThrow(() -> new WorkflowExecutionException(Category.NOT_FOUND));
        RuntimeInputs.require(activity.getInstanceId().equals(command.instanceId()), Category.NOT_FOUND);
        RuntimeInputs.require(reassignmentDecision(() -> adapter.canAccess(new TaskEligibilityContext(RuntimeViews.task(task, activity), context.actorRef()))), Category.NOT_FOUND);
        return new EnteredStage(activity, task);
    }

    private Optional<WorkflowExecutionResult> reassignmentReplay(io.github.leontecristian.workflow.core.dto.runtime.ReassignWorkflowTask command,
            WorkflowCommandContext context) {
        return replay(command.commandId(), WorkflowOperation.REASSIGN_TASK, context, json.reassignmentFingerprint(command, context)).map(result -> {
            RuntimeInputs.require(result.instance().id() == command.instanceId() && result.task().id() == command.taskId()
                    && result.task().assignment().equals(command.assignment()) && result.instance().lockVersion() > command.expectedLockVersion(), Category.HOST_CONFIGURATION);
            return result;
        });
    }

    /** Lookup only: current business access and original receipt actor remain mandatory. */
    public Optional<WorkflowExecutionResult> findReplay(io.github.leontecristian.workflow.core.dto.runtime.ReassignWorkflowTask command,
            WorkflowCommandContext context) {
        validateReassignment(command, context);
        return RuntimePersistenceErrors.call(() -> {
            reassignmentAccess(command, context, reassignmentAdapter());
            return reassignmentReplay(command, context);
        });
    }

    /** Replace only the current target and owner, preserving identity, obligations and frozen inputs. */
    @Transactional(propagation = Propagation.MANDATORY)
    public WorkflowCommandResult reassignTask(io.github.leontecristian.workflow.core.dto.runtime.ReassignWorkflowTask command,
            WorkflowCommandContext context) {
        return guarded(() -> {
            validateReassignment(command, context);
            var adapter = reassignmentAdapter();
            var stage = reassignmentAccess(command, context, adapter);
            var replay = reassignmentReplay(command, context);
            if (replay.isPresent()) return new WorkflowCommandResult(true, replay.get());
            RuntimeInputs.require(reassignmentDecision(() -> adapter.canReassign(new TaskEligibilityContext(
                    RuntimeViews.task(stage.task(), stage.activity()), context.actorRef()))), Category.REASSIGNMENT_FORBIDDEN);
            var instance = instances.findById(command.instanceId()).orElseThrow(() -> new WorkflowExecutionException(Category.NOT_FOUND));
            entityManager.refresh(instance);
            RuntimeInputs.require(instance.getLockVersion() == command.expectedLockVersion(), Category.STALE_VERSION);
            RuntimeInputs.require(instance.getStatus() == InstanceStatus.ACTIVE && instance.getLastHistorySequence() >= 4, Category.INVALID_STATE);
            long sequence = instance.reserveHistorySequence();
            entityManager.flush(); // Version-checked aggregate claim serializes all competing commands.
            var task = stage.task(); var activity = stage.activity();
            entityManager.refresh(activity); entityManager.refresh(task);
            // Host grants may depend on the assignment/owner cached before the aggregate claim.
            // Recheck the serialized current task only on a fresh mutation; receipt replay stays privilege-independent.
            var currentAccess = new TaskEligibilityContext(RuntimeViews.task(task, activity), context.actorRef());
            RuntimeInputs.require(reassignmentDecision(() -> adapter.canAccess(currentAccess)), Category.NOT_FOUND);
            RuntimeInputs.require(reassignmentDecision(() -> adapter.canReassign(currentAccess)), Category.REASSIGNMENT_FORBIDDEN);
            RuntimeInputs.require(activity.getStatus() == ActivityStatus.ACTIVE && task.getStatus() == TaskStatus.OPEN
                    && instance.getCurrentNodeKey().equals(activity.getNodeKey()), Category.INVALID_STATE);
            RuntimeInputs.require(!command.assignment().equals(task.getAssignment()), Category.CONFLICT);
            var time = RuntimeInputs.validateContextAndGetOccurredAtUtc(context);
            RuntimeInputs.require(!time.isBefore(task.getCreatedAt()), Category.INVALID_INPUT);
            var definition = pins.load(instance);
            var human = definition.model().humans().get(activity.getNodeKey());
            RuntimeInputs.require(human != null, Category.HOST_CONFIGURATION);
            if (!human.inputs().isEmpty()) RuntimeTaskCreationEvidence.read(history.findCreationByTaskId(task.getId())
                    .orElseThrow(() -> new WorkflowExecutionException(Category.HOST_CONFIGURATION)), task, activity, human.inputs(), json, history);
            RuntimeInputs.require(reassignmentDecision(() -> adapter.isValidTarget(new TaskEligibilityContext(
                    RuntimeViews.task(task, activity), context.actorRef()), command.assignment())), Category.INVALID_ASSIGNMENT);
            eligibility.requireTarget(new AssignmentContext(command.commandId(), instance.getId(), definition.versionId(),
                    definition.key(), definition.version(), definition.contentHash(), activity.getNodeKey(),
                    instance.getBusinessType(), instance.getBusinessId(), context), command.assignment());
            var previous = task.getAssignment(); String previousAssignee = task.getAssigneeRef();
            task.reassign(command.assignment());
            var details = json.object();
            var before = json.object(); before.put("kind", previous.kind().name()); before.put("ref", previous.ref());
            var after = json.object(); after.put("kind", task.getAssignmentKind().name()); after.put("ref", task.getAssignmentRef());
            details.set("previousAssignment", before); details.set("assignment", after);
            details.put("previousAssigneeRef", previousAssignee); details.put("assigneeRef", task.getAssigneeRef()); details.put("reason", command.reason());
            var batch = new RuntimeHistoryBatch(entityManager, json, instance.getId(), definition.versionId(), command.commandId(), context);
            batch.beginAt(sequence - 1);
            batch.next("TASK_REASSIGNED", activity.getId(), task.getId(), details, Map.of(
                    "previousAssignmentKind", previous.kind().name(), "previousAssignmentRef", previous.ref(),
                    "assignmentKind", task.getAssignmentKind().name(), "assignmentRef", task.getAssignmentRef()), (short) 1);
            return finish(command.commandId(), WorkflowOperation.REASSIGN_TASK, context, json.reassignmentFingerprint(command, context),
                    definition, instance, activity, task, sequence, batch, host.recorder(), RuntimeJson.FINGERPRINT_VERSION);
        });
    }

    private record OwnershipRequest(UUID commandId, WorkflowOperation operation, long instanceId, long taskId,
                                    long expectedLockVersion, String reason) {
        void validate(WorkflowCommandContext context) {
            RuntimeInputs.validateContextAndGetOccurredAtUtc(context);
            RuntimeInputs.require(commandId != null && instanceId > 0 && taskId > 0 && expectedLockVersion >= 0
                    && (!administrative() || RuntimeInputs.isValidText(reason, 1000, 1000, true)), Category.INVALID_INPUT);
            RuntimeInputs.require(context.actorKind() == ActorKind.USER, Category.NOT_ASSIGNED);
        }

        boolean administrative() { return operation == WorkflowOperation.ADMIN_RELEASE_TASK; }
    }

    private static OwnershipRequest ownershipRequest(ClaimWorkflowTask command) {
        RuntimeInputs.require(command != null, Category.INVALID_INPUT);
        return new OwnershipRequest(command.commandId(), WorkflowOperation.CLAIM_TASK, command.instanceId(),
                command.taskId(), command.expectedLockVersion(), null);
    }

    private static OwnershipRequest ownershipRequest(ReleaseWorkflowTask command) {
        RuntimeInputs.require(command != null, Category.INVALID_INPUT);
        return new OwnershipRequest(command.commandId(), WorkflowOperation.RELEASE_TASK, command.instanceId(),
                command.taskId(), command.expectedLockVersion(), null);
    }

    private static OwnershipRequest ownershipRequest(AdministrativelyReleaseWorkflowTask command) {
        RuntimeInputs.require(command != null, Category.INVALID_INPUT);
        return new OwnershipRequest(command.commandId(), WorkflowOperation.ADMIN_RELEASE_TASK, command.instanceId(),
                command.taskId(), command.expectedLockVersion(), command.reason());
    }

    private EnteredStage authorizedTask(OwnershipRequest request, WorkflowCommandContext context) {
        var task = tasks.findById(request.taskId()).orElseThrow(() -> new WorkflowExecutionException(Category.NOT_FOUND));
        var activity = activities.findById(task.getActivityId()).orElseThrow(() -> new WorkflowExecutionException(Category.NOT_FOUND));
        RuntimeInputs.require(activity.getInstanceId().equals(request.instanceId()), Category.NOT_FOUND);
        eligibility.requireOwnershipAccess(new TaskEligibilityContext(RuntimeViews.task(task, activity), context.actorRef()));
        return new EnteredStage(activity, task);
    }

    private Optional<WorkflowExecutionResult> findOwnershipReplay(OwnershipRequest request, WorkflowCommandContext context) {
        request.validate(context);
        return RuntimePersistenceErrors.call(() -> {
            authorizedTask(request, context);
            return ownershipReplay(request, context, ownershipFingerprint(request, context));
        });
    }

    private byte[] ownershipFingerprint(OwnershipRequest request, WorkflowCommandContext context) {
        return json.ownershipFingerprint(request.operation(), context, request.instanceId(), request.taskId(),
                request.expectedLockVersion(), request.reason());
    }

    private Optional<WorkflowExecutionResult> ownershipReplay(OwnershipRequest request, WorkflowCommandContext context,
            byte[] fingerprint) {
        return replay(request.commandId(), request.operation(), context, fingerprint).map(result -> {
            RuntimeInputs.require(result.instance().id() == request.instanceId() && result.task().id() == request.taskId()
                    && result.instance().lockVersion() > request.expectedLockVersion(), Category.HOST_CONFIGURATION);
            return result;
        });
    }

    private WorkflowCommandResult changeOwnership(OwnershipRequest request, WorkflowCommandContext context) {
        return guarded(() -> {
            request.validate(context);
            var stage = authorizedTask(request, context);
            byte[] fingerprint = ownershipFingerprint(request, context);
            var replay = ownershipReplay(request, context, fingerprint);
            if (replay.isPresent()) return new WorkflowCommandResult(true, replay.get());

            var instance = instances.findById(request.instanceId())
                    .orElseThrow(() -> new WorkflowExecutionException(Category.NOT_FOUND));
            var activity = stage.activity();
            var task = stage.task();
            entityManager.refresh(instance);
            RuntimeInputs.require(instance.getLockVersion() == request.expectedLockVersion(), Category.STALE_VERSION);
            RuntimeInputs.require(instance.getStatus() == InstanceStatus.ACTIVE && instance.getLastHistorySequence() >= 4,
                    Category.INVALID_STATE);
            RuntimeProgression.advance(instance.getLastHistorySequence(), 1);
            long sequence = instance.reserveHistorySequence();
            entityManager.flush(); // Serialize against all existing-instance writes before mutating a task.
            entityManager.refresh(activity);
            entityManager.refresh(task);
            RuntimeInputs.require(activity.getStatus() == ActivityStatus.ACTIVE && task.getStatus() == TaskStatus.OPEN
                    && instance.getCurrentNodeKey().equals(activity.getNodeKey()), Category.INVALID_STATE);
            RuntimeInputs.require(task.getAssignmentKind() != AssignmentTargetKind.USER, Category.TASK_NOT_CLAIMABLE);
            var time = RuntimeInputs.validateContextAndGetOccurredAtUtc(context);
            RuntimeInputs.require(!time.isBefore(task.getCreatedAt()), Category.INVALID_INPUT);
            var definition = pins.load(instance);
            var human = definition.model().humans().get(activity.getNodeKey());
            RuntimeInputs.require(human != null, Category.HOST_CONFIGURATION);
            if (!human.inputs().isEmpty()) {
                var created = history.findCreationByTaskId(task.getId())
                        .orElseThrow(() -> new WorkflowExecutionException(Category.HOST_CONFIGURATION));
                RuntimeTaskCreationEvidence.read(created, task, activity, human.inputs(), json, history);
            }
            RuntimeHistoryBatch batch = new RuntimeHistoryBatch(entityManager, json, instance.getId(), definition.versionId(),
                    request.commandId(), context);
            batch.beginAt(sequence - 1);
            recordOwnershipChange(request, context.actorRef(), activity, task, batch);
            return finish(request.commandId(), request.operation(), context, fingerprint, definition, instance, activity, task,
                    sequence, batch, host.recorder(), RuntimeJson.FINGERPRINT_VERSION);
        });
    }

    private void recordOwnershipChange(OwnershipRequest request, String actorRef, WorkflowActivityEntity activity,
            WorkflowTaskEntity task, RuntimeHistoryBatch batch) {
        var details = json.object();
        String previous = task.getAssigneeRef();
        details.put("previousAssigneeRef", previous);
        var view = RuntimeViews.task(task, activity);
        if (request.operation() == WorkflowOperation.CLAIM_TASK) {
            RuntimeInputs.require(eligibility.isEligible(view, actorRef), Category.NOT_ASSIGNED);
            RuntimeInputs.require(previous == null, Category.TASK_ALREADY_ASSIGNED);
            task.claim(actorRef);
            details.put("assigneeRef", actorRef);
            batch.next("TASK_CLAIMED", activity.getId(), task.getId(), null, details, Map.of("assigneeRef", actorRef));
            return;
        }
        if (request.administrative()) {
            RuntimeInputs.require(eligibility.canAdministrativelyRelease(new TaskEligibilityContext(view, actorRef)),
                    Category.ADMIN_RELEASE_FORBIDDEN);
        }
        RuntimeInputs.require(previous != null, Category.TASK_NOT_ASSIGNED);
        if (!request.administrative()) {
            RuntimeInputs.require(previous.equals(actorRef), Category.TASK_ASSIGNED_TO_OTHER);
        }
        task.release();
        details.putNull("assigneeRef");
        details.put("administrative", request.administrative());
        details.put("reason", request.reason());
        batch.next("TASK_RELEASED", activity.getId(), task.getId(), null, details,
                Map.of("previousAssigneeRef", previous, "administrative", Boolean.toString(request.administrative())));
    }

    private record EnteredStage(WorkflowActivityEntity activity, WorkflowTaskEntity task) { }

    private EnteredStage enterStage(ExecutableDefinition definition, WorkflowInstanceEntity instance, String nodeKey,
            UUID commandId, WorkflowCommandContext context, RuntimeHistoryBatch batch, Map<String, WorkflowValue> state,
            Map<String,io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue> submissions) {
        var human = definition.model().humans().get(nodeKey);
        RuntimeInputs.require(human != null, Category.HOST_CONFIGURATION);
        var inputs = human.inputs().isEmpty() ? null : RuntimeTaskCreationEvidence.capture(human.inputs(),
                input -> services.resolveInput(definition.model(), input.binding(), instance.getId(), state,submissions), json);
        var taskType = catalog.findByCode(human.taskType())
                .orElseThrow(() -> new WorkflowExecutionException(Category.HOST_CONFIGURATION));
        var time = RuntimeInputs.validateContextAndGetOccurredAtUtc(context);
        var assignmentContext = new AssignmentContext(commandId, instance.getId(), definition.versionId(),
                definition.key(), definition.version(), definition.contentHash(), human.key(),instance.getBusinessType(),instance.getBusinessId(),context);
        List<io.github.leontecristian.workflow.core.dto.runtime.ResolvedTask> roster;
        String revision;
        if (human.completionPolicy() == CompletionPolicy.ALL) {
            var assignment = host.resolveAll(human.assignment().resolver(),assignmentContext);
            roster = assignment.tasks().stream().sorted(java.util.Comparator.comparing(io.github.leontecristian.workflow.core.dto.runtime.ResolvedTask::taskKey)).toList();
            revision = assignment.reviewRevisionRef();
        } else {
            var assignment = host.resolve(host.resolver(human.assignment().resolver()),assignmentContext);
            roster = assignment.tasks(); revision = assignment.reviewRevisionRef();
        }
        long activation=RuntimeProgression.advance(batch.last(),1);
        var activity=new WorkflowActivityEntity(instance.getId(),human.key(),activation,taskType.id(),revision,time,human.completionPolicy(),human.contributionOutcome(),roster.size());
        entityManager.persist(activity);
        var entered=json.object(); entered.put("nodeKey",human.key()); entered.put("activationSequence",Long.toString(activation));
        entered.put("taskTypeId",Long.toString(taskType.id())); entered.put("taskTypeCode",taskType.code());
        entered.put("completionPolicy",human.completionPolicy().name()); entered.put("requiredTaskCount",roster.size()); entered.put("reviewRevisionRef",revision);
        if (human.contributionOutcome() != null) entered.put("contributionOutcomeKey",human.contributionOutcome());
        batch.next("ACTIVITY_ENTERED",activity.getId(),null,null,entered,Map.of());
        Long snapshotSequence = null;
        if (inputs != null && human.completionPolicy() == CompletionPolicy.ALL) {
            var shared = json.object(); shared.set("inputs",inputs);
            snapshotSequence = RuntimeProgression.advance(batch.last(),1);
            batch.next("ACTIVITY_INPUTS_CAPTURED",activity.getId(),null,shared,Map.of(),(short)1);
        }
        WorkflowTaskEntity representative = null;
        for (var resolved : roster) {
        var task=new WorkflowTaskEntity(activity.getId(),resolved.taskKey(),resolved.assignment(),time); entityManager.persist(task);
        if (representative == null) representative = task;
        var created = json.object(); created.put("taskKey", resolved.taskKey()); created.set("assignment", json.tree(json.encode(resolved.assignment())));
        created.put("assigneeRef", task.getAssigneeRef());
        var payload = new java.util.HashMap<>(Map.of("taskKey", resolved.taskKey(),
                "assignmentKind", resolved.assignment().kind().name(), "assignmentRef", resolved.assignment().ref()));
        if (task.getAssigneeRef() != null) payload.put("assigneeRef", task.getAssigneeRef());
        if (inputs == null) {
            batch.next("TASK_CREATED", activity.getId(), task.getId(), null, created, payload);
        } else if (snapshotSequence != null) {
            created.put("inputSnapshotSequence",Long.toString(snapshotSequence));
            batch.next("TASK_CREATED",activity.getId(),task.getId(),created,payload,(short)6);
        } else {
            created.set("inputs", inputs);
            batch.next("TASK_CREATED", activity.getId(), task.getId(), created, payload, RuntimeTaskCreationEvidence.INPUTS_VERSION);
        }
        }
        return new EnteredStage(activity,representative);
    }

    private Optional<WorkflowExecutionResult> replay(UUID commandId, WorkflowOperation operation, WorkflowCommandContext context, byte[] fingerprint) {
        return json.replay(receipts.findById(commandId).orElse(null), commandId, operation, context, fingerprint);
    }

    private Optional<WorkflowExecutionResult> startReplay(StartWorkflow command, WorkflowCommandContext context, byte[] fingerprint) {
        return json.replay(receipts.findById(command.commandId()).orElse(null), command.commandId(),
                WorkflowOperation.START_WORKFLOW, context, fingerprint, json.fingerprintVersion(command)).map(result -> {
            RuntimeInputs.require((command.definitionVersionId() == null || result.instance().definitionVersionId() == command.definitionVersionId())
                    && java.util.Objects.equals(result.instance().businessType(), command.businessType())
                    && java.util.Objects.equals(result.instance().businessId(), command.businessId()), Category.HOST_CONFIGURATION);
            return result;
        });
    }

    private Optional<WorkflowExecutionResult> completionReplay(CompleteWorkflowTask command, WorkflowCommandContext context, byte[] fingerprint) {
        return json.replay(receipts.findById(command.commandId()).orElse(null), command.commandId(),
                WorkflowOperation.COMPLETE_TASK, context, fingerprint, json.fingerprintVersion(command)).map(result -> {
            RuntimeInputs.require(result.instance().id() == command.instanceId() && result.task().id() == command.taskId()
                    && command.outcomeKey().equals(result.task().outcomeKey()) && result.instance().lockVersion() > command.expectedLockVersion(),
                    Category.HOST_CONFIGURATION);
            return result;
        });
    }

    private WorkflowCommandResult finish(UUID commandId, WorkflowOperation operation, WorkflowCommandContext context, byte[] fingerprint,
            ExecutableDefinition definition, WorkflowInstanceEntity instance, WorkflowActivityEntity activity, WorkflowTaskEntity task,
            long first, RuntimeHistoryBatch batch, WorkflowEventRecorder recorder, String fingerprintVersion) {
        entityManager.flush(); // Capture actual final managed token after every instance update, not a predicted increment.
        WorkflowExecutionResult result = snapshot(commandId, operation, definition, instance, activity, task, first);
        entityManager.persist(new WorkflowCommandReceiptEntity(commandId, instance.getId(), operation.name(), context.actorKind(), context.actorRef(),
                fingerprint, fingerprintVersion, RuntimeInputs.validateContextAndGetOccurredAtUtc(context), json.evidence(result), (short)(operation == WorkflowOperation.REASSIGN_TASK ? 8 : activity.getCompletionPolicy() == CompletionPolicy.ALL ? 6 : 5)));
        entityManager.flush(); // Detect a receipt-PK loser before invoking the durable recorder; never recover in this transaction.
        host.record(recorder, batch.events());
        entityManager.flush();
        RuntimeInputs.require(result.equals(snapshot(commandId, operation, definition, instance, activity, task, first)), Category.HOST_CONFIGURATION);
        return new WorkflowCommandResult(false, result);
    }

    private WorkflowExecutionResult snapshot(UUID commandId, WorkflowOperation operation, ExecutableDefinition definition,
            WorkflowInstanceEntity instance, WorkflowActivityEntity activity, WorkflowTaskEntity task, long first) {
        return new WorkflowExecutionResult(commandId, operation, RuntimeViews.instance(instance, definition),
                RuntimeViews.activity(activity), RuntimeViews.task(task, activity), first, instance.getLastHistorySequence());
    }

    private <T> T guarded(Supplier<T> command) {
        RuntimeInputs.require(!Boolean.TRUE.equals(executing.get()), Category.HOST_CONFIGURATION);
        executing.set(true);
        try {
            return RuntimePersistenceErrors.call(command);
        } finally {
            executing.remove();
        }
    }
}
