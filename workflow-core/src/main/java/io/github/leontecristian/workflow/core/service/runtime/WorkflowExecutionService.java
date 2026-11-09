package io.github.leontecristian.workflow.core.service.runtime;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
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
import jakarta.persistence.EntityManager;
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
    private final ThreadLocal<Boolean> executing = new ThreadLocal<>();

    public WorkflowExecutionService(WorkflowInstanceRepository instances, WorkflowActivityRepository activities,
            WorkflowTaskRepository tasks, WorkflowHistoryRepository history, WorkflowCommandReceiptRepository receipts,
            EntityManager entityManager, WorkflowDefinitionRuntimeReader definitions, TaskTypeCatalogService catalog,
            RuntimeJson json, RuntimeHostSupport host, RuntimeDefinitionPins pins, RuntimeVariables variables, RuntimeServiceProgression services, WorkflowTaskEligibilityService eligibility) {
        this.eligibility = eligibility;
        this.variables=variables; this.services=services;
        this.instances = instances; this.activities = activities; this.tasks = tasks; this.history = history; this.receipts = receipts;
        this.entityManager = entityManager; this.definitions = definitions; this.catalog = catalog; this.json = json; this.host = host; this.pins = pins;
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
            RuntimeInputs.start(command, context);
            RuntimeValueBindings.basic(inputs,json);
            RuntimeInputs.require((command.hostStartIntent() == null) == (links == null), Category.INVALID_INPUT);
            byte[] fingerprint = json.fingerprint(command, inputs, context);
            var replay = startReplay(command, context, fingerprint);
            if (replay.isPresent()) return new WorkflowCommandResult(true, replay.get());
            var recorder = host.recorder();
            var definition = definitions.loadForStart(command.definitionVersionId());
            definitions.requireCapabilities(definition, true);
            var model = definition.model();
            var acceptedInputs=RuntimeValueBindings.inputs(model,inputs,json);
            var time = RuntimeInputs.context(context);
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
            var paused=services.traverse(model,model.start().key(),null,new ServiceActionContext(
                    command.commandId(),instance.getId(),definition.versionId(),model.start().key(),command.businessType(),command.businessId(),context),state,progression,batch,definition.contentHash(),null);
            RuntimeInputs.require("HUMAN_TASK".equals(paused.type()),Category.HOST_CONFIGURATION);
            var stage = enterStage(definition,instance,paused.key(),command.commandId(),context,batch);
            instance.waitAt(paused.key(),batch.last());
            return finish(command.commandId(), WorkflowOperation.START_WORKFLOW, context, fingerprint, definition, instance, stage.activity(), stage.task(), 1, batch, recorder, json.fingerprintVersion(inputs));
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public WorkflowCommandResult completeTask(CompleteWorkflowTask command, WorkflowCommandContext context) {
        return guarded(() -> {
            RuntimeInputs.complete(command, context);
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
                    && activity.getTaskTypeId() == taskType.id() && activity.getCompletionPolicy() == CompletionPolicy.SINGLE
                    && activity.getRequiredTaskCount() == 1 && activity.getActivationSequence() > 0 && activity.getActivationSequence() < first
                    && activity.getContributionOutcomeKey() == null, Category.HOST_CONFIGURATION);
            var roster = tasks.findPageByActivityId(activity.getId(), 0, PageRequest.of(0, 2));
            RuntimeInputs.require(roster.size() == 1 && roster.get(0).getId().equals(task.getId()), Category.HOST_CONFIGURATION);
            RuntimeInputs.require(history.findCompletionByTaskId(task.getId()).isEmpty(), Category.HOST_CONFIGURATION);
            var created = history.findById(new WorkflowHistoryId(instance.getId(), RuntimeProgression.advance(activity.getActivationSequence(),1)))
                    .orElseThrow(() -> new WorkflowExecutionException(Category.HOST_CONFIGURATION));
            RuntimeInputs.require("TASK_CREATED".equals(created.getEventType()) && task.getId().equals(created.getTaskId())
                    && activity.getId().equals(created.getActivityId()), Category.HOST_CONFIGURATION);
            RuntimeInputs.require(created.getDetailsSchemaVersion() >= 2 && created.getDetailsSchemaVersion() <= 4, Category.UNSUPPORTED_VERSION);
            RuntimeInputs.require(RuntimeTaskCreationEvidence.matches(created, task, json), Category.HOST_CONFIGURATION);
            var entered = history.findById(new WorkflowHistoryId(instance.getId(), activity.getActivationSequence()))
                    .orElseThrow(() -> new WorkflowExecutionException(Category.HOST_CONFIGURATION));
            RuntimeInputs.require("ACTIVITY_ENTERED".equals(entered.getEventType()) && activity.getId().equals(entered.getActivityId())
                    && entered.getTaskId() == null && entered.getCommandId().equals(created.getCommandId())
                    && entered.getOccurredAt().equals(activity.getEnteredAt())
                    && entered.getActorKind() == created.getActorKind() && entered.getActorRef().equals(created.getActorRef()), Category.HOST_CONFIGURATION);
            var selectedOutcome = human.outcome(command.outcomeKey())
                    .orElseThrow(() -> new WorkflowExecutionException(Category.INVALID_INPUT));
            RuntimeInputs.require(!selectedOutcome.requiresComment() || (command.comment() != null && !command.comment().isBlank()), Category.INVALID_INPUT);
            var time = RuntimeInputs.context(context);
            RuntimeInputs.require(!time.isBefore(instance.getStartedAt()) && !time.isBefore(activity.getEnteredAt()) && !time.isBefore(task.getCreatedAt()), Category.INVALID_INPUT);
            RuntimeInputs.require(eligibility.isEligible(RuntimeViews.task(task, activity), context.actorRef()), Category.NOT_ASSIGNED);
            if (task.getAssigneeRef() != null) {
                RuntimeInputs.require(task.getAssigneeRef().equals(context.actorRef()), Category.TASK_ASSIGNED_TO_OTHER);
            } else {
                RuntimeInputs.require(task.getAssignmentKind() != AssignmentTargetKind.USER, Category.HOST_CONFIGURATION);
                RuntimeInputs.require(human.claimPolicy() != TaskClaimPolicy.REQUIRED, Category.CLAIM_REQUIRED);
            }
            task.complete(command.outcomeKey(), time, context.actorRef()); activity.complete(command.outcomeKey(), time, context.actorRef());
            RuntimeHistoryBatch batch = new RuntimeHistoryBatch(entityManager, json, instance.getId(), definition.versionId(), command.commandId(), context);
            var submitted = json.object(); submitted.put("outcomeKey", command.outcomeKey()); submitted.put("comment", command.comment()); submitted.putObject("inputs");
            batch.beginAt(first-1);
            batch.append(first, "TASK_COMPLETED", activity.getId(), task.getId(), null, submitted, Map.of("outcomeKey", command.outcomeKey()));
            batch.append(RuntimeProgression.advance(first, 1), "ACTIVITY_COMPLETED", activity.getId(), null, null,
                    Map.of("outcomeKey", command.outcomeKey(), "closureReason", "TASK_OUTCOME"), Map.of("outcomeKey", command.outcomeKey()));
            var budget=new RuntimeProgression();
            var end=services.traverse(model,human.key(),null,new ServiceActionContext(
                    command.commandId(),instance.getId(),definition.versionId(),human.key(),instance.getBusinessType(),instance.getBusinessId(),context),
                    variables.read(instance.getId(),model),budget,batch,definition.contentHash(),selectedOutcome.key());
            if ("HUMAN_TASK".equals(end.type())) {
                entityManager.flush(); // Close the previous active row before inserting its successor.
                enterStage(definition,instance,end.key(),command.commandId(),context,batch);
                instance.advanceToHuman(end.key(),batch.last());
            } else {
                budget.visit(end,"END");
                batch.next("INSTANCE_COMPLETED",null,null,null,Map.of("resultKey",end.result()),Map.of("resultKey",end.result()));
                instance.complete(end.key(),end.result(),batch.last(),time,context.actorRef());
            }
            return finish(command.commandId(), WorkflowOperation.COMPLETE_TASK, context, fingerprint, definition, instance, activity, task, first, batch, recorder, RuntimeJson.FINGERPRINT_VERSION);
        });
    }

    public Optional<WorkflowCommandResult> findReplay(StartWorkflow command, WorkflowCommandContext context) {
        return findReplay(command,StartInputs.empty(),context);
    }
    public Optional<WorkflowCommandResult> findReplay(StartWorkflow command, StartInputs inputs, WorkflowCommandContext context) {
        RuntimeInputs.start(command,context); RuntimeValueBindings.basic(inputs,json);
        return RuntimePersistenceErrors.call(() -> startReplay(command, context, json.fingerprint(command, inputs, context))
                .map(result -> new WorkflowCommandResult(true, result)));
    }

    public Optional<WorkflowCommandResult> findReplay(CompleteWorkflowTask command, WorkflowCommandContext context) {
        RuntimeInputs.complete(command, context);
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

    private record OwnershipRequest(UUID commandId, WorkflowOperation operation, long instanceId, long taskId,
                                    long expectedLockVersion, String reason) {
        void validate(WorkflowCommandContext context) {
            RuntimeInputs.context(context);
            RuntimeInputs.require(commandId != null && instanceId > 0 && taskId > 0 && expectedLockVersion >= 0
                    && (!administrative() || RuntimeInputs.text(reason, 1000, 1000, true)), Category.INVALID_INPUT);
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
            var time = RuntimeInputs.context(context);
            RuntimeInputs.require(!time.isBefore(task.getCreatedAt()), Category.INVALID_INPUT);
            var definition = pins.load(instance);
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
            UUID commandId, WorkflowCommandContext context, RuntimeHistoryBatch batch) {
        var human = definition.model().humans().get(nodeKey);
        RuntimeInputs.require(human != null, Category.HOST_CONFIGURATION);
        var resolver = host.resolver(human.assignment().resolver());
        var taskType = catalog.findByCode(human.taskType())
                .orElseThrow(() -> new WorkflowExecutionException(Category.HOST_CONFIGURATION));
        var time = RuntimeInputs.context(context);
        var assignment = host.resolve(resolver, new AssignmentContext(commandId, instance.getId(), definition.versionId(),
                definition.key(), definition.version(), definition.contentHash(), human.key(),instance.getBusinessType(),instance.getBusinessId(),context));
        var resolved=assignment.tasks().get(0);
        long activation=RuntimeProgression.advance(batch.last(),1);
        var activity=new WorkflowActivityEntity(instance.getId(),human.key(),activation,taskType.id(),assignment.reviewRevisionRef(),time);
        entityManager.persist(activity);
        var task=new WorkflowTaskEntity(activity.getId(),resolved.taskKey(),resolved.assignment(),time); entityManager.persist(task);
        var entered=json.object(); entered.put("nodeKey",human.key()); entered.put("activationSequence",Long.toString(activation));
        entered.put("taskTypeId",Long.toString(taskType.id())); entered.put("taskTypeCode",taskType.code());
        entered.put("completionPolicy","SINGLE"); entered.put("requiredTaskCount",1); entered.put("reviewRevisionRef",assignment.reviewRevisionRef());
        batch.next("ACTIVITY_ENTERED",activity.getId(),null,null,entered,Map.of());
        var created = json.object(); created.put("taskKey", resolved.taskKey()); created.set("assignment", json.tree(json.encode(resolved.assignment())));
        created.put("assigneeRef", task.getAssigneeRef());
        var payload = new java.util.HashMap<>(Map.of("taskKey", resolved.taskKey(),
                "assignmentKind", resolved.assignment().kind().name(), "assignmentRef", resolved.assignment().ref()));
        if (task.getAssigneeRef() != null) payload.put("assigneeRef", task.getAssigneeRef());
        batch.next("TASK_CREATED", activity.getId(), task.getId(), null, created, payload);
        return new EnteredStage(activity,task);
    }

    private Optional<WorkflowExecutionResult> replay(UUID commandId, WorkflowOperation operation, WorkflowCommandContext context, byte[] fingerprint) {
        return json.replay(receipts.findById(commandId).orElse(null), commandId, operation, context, fingerprint);
    }

    private Optional<WorkflowExecutionResult> startReplay(StartWorkflow command, WorkflowCommandContext context, byte[] fingerprint) {
        return replay(command.commandId(), WorkflowOperation.START_WORKFLOW, context, fingerprint).map(result -> {
            RuntimeInputs.require(result.instance().definitionVersionId() == command.definitionVersionId()
                    && java.util.Objects.equals(result.instance().businessType(), command.businessType())
                    && java.util.Objects.equals(result.instance().businessId(), command.businessId()), Category.HOST_CONFIGURATION);
            return result;
        });
    }

    private Optional<WorkflowExecutionResult> completionReplay(CompleteWorkflowTask command, WorkflowCommandContext context, byte[] fingerprint) {
        return replay(command.commandId(), WorkflowOperation.COMPLETE_TASK, context, fingerprint).map(result -> {
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
                fingerprint, fingerprintVersion, RuntimeInputs.context(context), json.evidence(result), (short)5));
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
        try { return RuntimePersistenceErrors.call(command); }
        finally { executing.remove(); }
    }
}
