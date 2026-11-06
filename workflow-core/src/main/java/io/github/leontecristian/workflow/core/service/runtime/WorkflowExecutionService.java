package io.github.leontecristian.workflow.core.service.runtime;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionContext;
import io.github.leontecristian.workflow.core.dto.runtime.StartInputs;

import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
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
    private final RuntimeDefinitionPins pins;
    private final RuntimeVariables variables;
    private final RuntimeServiceProgression services;
    private final ThreadLocal<Boolean> executing = new ThreadLocal<>();

    public WorkflowExecutionService(WorkflowInstanceRepository instances, WorkflowActivityRepository activities,
            WorkflowTaskRepository tasks, WorkflowHistoryRepository history, WorkflowCommandReceiptRepository receipts,
            EntityManager entityManager, WorkflowDefinitionRuntimeReader definitions, TaskTypeCatalogService catalog,
            RuntimeJson json, RuntimeHostSupport host, RuntimeDefinitionPins pins, RuntimeVariables variables, RuntimeServiceProgression services) {
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
            var resolver = host.resolver(model.human().assignment().resolver());
            var taskType = catalog.findByCode(model.human().taskType())
                    .orElseThrow(() -> new WorkflowExecutionException(Category.HOST_CONFIGURATION));
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
            var paused=services.traverse(model,model.start().key(),model.human().key(),new ServiceActionContext(
                    command.commandId(),instance.getId(),definition.versionId(),model.start().key(),command.businessType(),command.businessId(),context),state,progression,batch,definition.contentHash(),null);
            RuntimeInputs.require("HUMAN_TASK".equals(paused.type()),Category.HOST_CONFIGURATION);
            var assignment = host.resolve(resolver, new AssignmentContext(command.commandId(), instance.getId(), definition.versionId(),
                    definition.key(), definition.version(), definition.contentHash(), model.human().key(),command.businessType(),command.businessId(),context));
            var resolved=assignment.tasks().get(0);
            long activation=RuntimeProgression.advance(batch.last(),1);
            var activity=new WorkflowActivityEntity(instance.getId(),model.human().key(),activation,taskType.id(),assignment.reviewRevisionRef(),time);
            entityManager.persist(activity);
            var task=new WorkflowTaskEntity(activity.getId(),resolved.taskKey(),resolved.assignedUserRef(),time); entityManager.persist(task);
            var entered=json.object(); entered.put("nodeKey",model.human().key()); entered.put("activationSequence",Long.toString(activation));
            entered.put("taskTypeId",Long.toString(taskType.id())); entered.put("taskTypeCode",taskType.code());
            entered.put("completionPolicy","SINGLE"); entered.put("requiredTaskCount",1); entered.put("reviewRevisionRef",assignment.reviewRevisionRef());
            batch.next("ACTIVITY_ENTERED",activity.getId(),null,null,entered,Map.of());
            Map<String,String> created=Map.of("taskKey",resolved.taskKey(),"assignedUserRef",resolved.assignedUserRef());
            batch.next("TASK_CREATED",activity.getId(),task.getId(),null,created,created);
            instance.waitAt(model.human().key(),batch.last());
            return finish(command.commandId(), WorkflowOperation.START_WORKFLOW, context, fingerprint, definition, instance, activity, task, 1, batch, recorder, json.fingerprintVersion(inputs));
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
            RuntimeInputs.require(task.getAssignedUserRef().equals(context.actorRef()), Category.NOT_ASSIGNED);
            var definition = pins.load(instance);
            definitions.requireCapabilities(definition, false);
            var model = definition.model();
            RuntimeInputs.require(model.extended() || (first==5 && activity.getActivationSequence()==3),Category.HOST_CONFIGURATION);
            var taskType = catalog.findByCode(model.human().taskType()).orElseThrow(() -> new WorkflowExecutionException(Category.HOST_CONFIGURATION));
            RuntimeInputs.require(instance.getCurrentNodeKey().equals(model.human().key()) && activity.getNodeKey().equals(model.human().key())
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
            RuntimeInputs.require(created.getDetailsSchemaVersion() == 2, Category.UNSUPPORTED_VERSION);
            var assignmentEvidence = json.tree(created.getDetailsJson());
            RuntimeInputs.require(assignmentEvidence.isObject() && assignmentEvidence.size() == 2
                    && assignmentEvidence.path("taskKey").isTextual() && assignmentEvidence.path("assignedUserRef").isTextual()
                    && task.getTaskKey().equals(assignmentEvidence.path("taskKey").textValue())
                    && task.getAssignedUserRef().equals(assignmentEvidence.path("assignedUserRef").textValue())
                    && task.getCreatedAt().equals(created.getOccurredAt()) && instance.getStartedBy().equals(created.getActorRef()),
                    Category.HOST_CONFIGURATION);
            var entered = history.findById(new WorkflowHistoryId(instance.getId(), activity.getActivationSequence()))
                    .orElseThrow(() -> new WorkflowExecutionException(Category.HOST_CONFIGURATION));
            RuntimeInputs.require("ACTIVITY_ENTERED".equals(entered.getEventType()) && activity.getId().equals(entered.getActivityId())
                    && entered.getTaskId() == null && entered.getCommandId().equals(created.getCommandId())
                    && entered.getOccurredAt().equals(activity.getEnteredAt()), Category.HOST_CONFIGURATION);
            var selectedOutcome = model.human().outcome(command.outcomeKey())
                    .orElseThrow(() -> new WorkflowExecutionException(Category.INVALID_INPUT));
            RuntimeInputs.require(!selectedOutcome.requiresComment() || (command.comment() != null && !command.comment().isBlank()), Category.INVALID_INPUT);
            var time = RuntimeInputs.context(context);
            RuntimeInputs.require(!time.isBefore(instance.getStartedAt()) && !time.isBefore(activity.getEnteredAt()) && !time.isBefore(task.getCreatedAt()), Category.INVALID_INPUT);
            task.complete(command.outcomeKey(), time, context.actorRef()); activity.complete(command.outcomeKey(), time, context.actorRef());
            RuntimeHistoryBatch batch = new RuntimeHistoryBatch(entityManager, json, instance.getId(), definition.versionId(), command.commandId(), context);
            var submitted = json.object(); submitted.put("outcomeKey", command.outcomeKey()); submitted.put("comment", command.comment()); submitted.putObject("inputs");
            batch.beginAt(first-1);
            batch.append(first, "TASK_COMPLETED", activity.getId(), task.getId(), null, submitted, Map.of("outcomeKey", command.outcomeKey()));
            batch.append(RuntimeProgression.advance(first, 1), "ACTIVITY_COMPLETED", activity.getId(), null, null,
                    Map.of("outcomeKey", command.outcomeKey(), "closureReason", "TASK_OUTCOME"), Map.of("outcomeKey", command.outcomeKey()));
            var budget=new RuntimeProgression();
            var end=services.traverse(model,model.human().key(),null,new ServiceActionContext(
                    command.commandId(),instance.getId(),definition.versionId(),model.human().key(),instance.getBusinessType(),instance.getBusinessId(),context),
                    variables.read(instance.getId(),model),budget,batch,definition.contentHash(),selectedOutcome.key());
            budget.visit(end,"END");
            batch.next("INSTANCE_COMPLETED",null,null,null,Map.of("resultKey",end.result()),Map.of("resultKey",end.result()));
            instance.complete(end.key(),end.result(),batch.last(),time,context.actorRef());
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
                fingerprint, fingerprintVersion, RuntimeInputs.context(context), json.evidence(result), (short)3));
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
