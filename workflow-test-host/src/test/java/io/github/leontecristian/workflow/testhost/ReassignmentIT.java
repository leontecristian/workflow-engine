package io.github.leontecristian.workflow.testhost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leontecristian.workflow.core.dto.definition.CheckWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionManagementContext;
import io.github.leontecristian.workflow.core.dto.definition.ImportWorkflowDraft;
import io.github.leontecristian.workflow.core.dto.definition.document.TaskResultContract.Mode;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.ActivityStatus;
import io.github.leontecristian.workflow.core.dto.runtime.AllAssignmentResult;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.CancelWorkflow;
import io.github.leontecristian.workflow.core.dto.runtime.ClaimWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.CompletionPolicy;
import io.github.leontecristian.workflow.core.dto.runtime.InstanceStatus;
import io.github.leontecristian.workflow.core.dto.runtime.ResolvedTask;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionContext;
import io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget;
import io.github.leontecristian.workflow.core.dto.runtime.TaskClosureReason;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCancellationCommandResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowExecutionResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistorySummary;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.dto.tasktype.CreateTaskType;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.definition.DefinitionCapabilityProvider;
import io.github.leontecristian.workflow.core.service.runtime.AllTaskAssignmentResolver;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowEventRecorder;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowExecutionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import io.github.leontecristian.workflow.testhost.configuration.integration.CompletionAdapterIntegrationTest;
import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleAdapters;
import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings;
import io.github.leontecristian.workflow.testhost.dto.runtime.SandboxStart;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxServiceEffectStore;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeTransaction;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleTaskOwnershipAuthorization;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleWorkflowCancellationAuthorization;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Committed reassignment boundaries; the inherited initializer permits only the dedicated test database. */
@TestPropertySource(properties = {"workflow.console.enabled=true", "workflow.console.capabilities=sample",
        "workflow.console.admin.username=reassign.admin", "workflow.console.admin.password=integration-only-admin",
        "workflow.console.reviewer.username=reassign.reviewer", "workflow.console.reviewer.password=integration-only-reviewer"})
class ReassignmentIT extends CompletionAdapterIntegrationTest {
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired WorkflowDefinitionService definitions;
    @Autowired io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionRuntimeReader pinnedDefinitions;
    @Autowired TaskTypeCatalogService catalog;
    @Autowired WorkflowRuntimeQueryService reads;
    @Autowired WorkflowExecutionService execution;
    @Autowired ConsoleRuntimeTransaction runtime;
    @Autowired SandboxRequestStore requests;
    @Autowired SandboxServiceEffectStore effects;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean ConsoleWorkflowCancellationAuthorization cancellation;
    @MockitoBean io.github.leontecristian.workflow.testhost.service.runtime.ConsoleTaskReassignmentAuthorization reassignment;
    @MockitoSpyBean ConsoleTaskOwnershipAuthorization ownership;
    @MockitoSpyBean WorkflowEventRecorder recorder;
    @MockitoBean(name = "consoleCapabilities") DefinitionCapabilityProvider capabilities;
    @Autowired ConsoleSettings settings;
    @MockitoSpyBean(name = "sandboxAllResolver") AllTaskAssignmentResolver resolver;
    private final String marker = "reassign.it." + UUID.randomUUID();
    private final ObjectMapper json = new ObjectMapper();
    private final List<Long> instances = new ArrayList<>();
    private Long familyId, versionId, ownedTaskType;

    private <T> T tx(Supplier<T> work) {
        var template = new TransactionTemplate(transactions);
        template.setTimeout(30);
        return template.execute(status -> work.get());
    }
    private WorkflowCommandContext actor(String name) {
        return new WorkflowCommandContext(ActorKind.USER, name, Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS), marker);
    }
    private WorkflowCommandContext admin() { return actor("reassign.admin"); }
    private WorkflowCommandContext reviewer() { return actor("reassign.reviewer"); }
    private long version(long instance) { return reads.findInstance(instance).orElseThrow().lockVersion(); }
    private CancelWorkflow command(long instance) { return new CancelWorkflow(UUID.randomUUID(), instance, version(instance), " stop exactly \n reason "); }
    private WorkflowCancellationCommandResult cancel(CancelWorkflow command) { return tx(() -> execution.cancelWorkflow(command, admin())); }
    private CompleteWorkflowTask completion(WorkflowExecutionResult start) {
        return new CompleteWorkflowTask(UUID.randomUUID(), start.instance().id(), start.task().id(), version(start.instance().id()),
                "APPROVE", null, start.activity().completionPolicy() == CompletionPolicy.ALL ? CompleteWorkflowTask.parseResult("{\"note\":\"retained\"}") : null);
    }
    private WorkflowCommandResult complete(CompleteWorkflowTask command) { return tx(() -> execution.completeTask(command, reviewer())); }
    private WorkflowCommandResult claim(WorkflowExecutionResult start, long expected) {
        return tx(() -> execution.claimTask(new ClaimWorkflowTask(UUID.randomUUID(), start.instance().id(), start.task().id(), expected), reviewer()));
    }
    private int count(String table, long instance) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE instance_id=?", Integer.class, instance); }
    private void failure(Runnable work, Category category) {
        assertThatThrownBy(work::run).isInstanceOfSatisfying(WorkflowExecutionException.class, e -> assertThat(e.category()).isEqualTo(category));
    }
    private static <T> T target(T bean) { return AopTestUtils.getUltimateTargetObject(bean); }

    @BeforeEach void authorizeDirectCoreCalls() {
        DefinitionCapabilityProvider delegate = ReflectionTestUtils.invokeMethod(new ConsoleAdapters(), "consoleCapabilities", settings);
        when(capabilities.snapshot()).thenAnswer(invocation -> java.util.Objects.requireNonNull(delegate).snapshot());
        when(reassignment.canAccess(any())).thenReturn(true);
        when(reassignment.canReassign(any())).thenReturn(true);
        when(reassignment.isValidTarget(any(), any())).thenReturn(true);
        when(cancellation.canAccess(any())).thenReturn(true);
        when(cancellation.canCancel(any())).thenReturn(true);
        doReturn(true).when(target(ownership)).canAccess(any());
    }
    private void publish(boolean all) throws Exception { publish(all, false); }
    private void publish(boolean all, boolean sequential) throws Exception {
        var path = Path.of(all ? "docs/features/F021-all-collective-review/sandbox-all-review.json"
                : "docs/features/F016-shared-group-role-task-assignment/finance-group-review.json");
        if (!Files.exists(path)) path = Path.of("..").resolve(path);
        var source = (ObjectNode) json.readTree(Files.readString(path));
        source.put("key", marker);
        if (sequential) {
            var nodes = (com.fasterxml.jackson.databind.node.ArrayNode) source.path("nodes");
            ObjectNode next = null;
            for (var node : nodes) if ("REVIEW".equals(node.path("key").asText())) next = ((ObjectNode) node).deepCopy();
            java.util.Objects.requireNonNull(next).put("key", "SECOND_REVIEW");
            nodes.add(next);
            var transitions = (com.fasterxml.jackson.databind.node.ArrayNode) source.path("transitions");
            for (var edge : transitions) if ("approve".equals(edge.path("key").asText())) ((ObjectNode) edge).put("to", "SECOND_REVIEW");
            transitions.addObject().put("key", "secondApprove").put("from", "SECOND_REVIEW").put("to", "SET_APPROVED").put("outcome", "APPROVE");
            transitions.addObject().put("key", "secondReject").put("from", "SECOND_REVIEW").put("to", "SET_REJECTED").put("outcome", "REJECT");
        }
        if (catalog.findByCode("SANDBOX_REVIEW").isEmpty())
            ownedTaskType = tx(() -> catalog.create(new CreateTaskType("SANDBOX_REVIEW", marker, null, true))).after().id();
        tx(() -> {
            var context = new DefinitionManagementContext(marker, Instant.now());
            var draft = definitions.importDraft(new ImportWorkflowDraft(source.toString()), context).after();
            familyId = draft.definitionId(); versionId = draft.id();
            var published = definitions.publish(new CheckWorkflowDefinition(versionId, draft.lockVersion()), context);
            assertThat(published.published()).withFailMessage(() -> published.report().toString()).isTrue();
            return null;
        });
    }
    private WorkflowExecutionResult start() {
        long request = tx(() -> requests.create(marker, marker, Instant.now())).id();
        var start = runtime.start(new SandboxStart(UUID.randomUUID(), versionId, request, 0),
                new WorkflowCommandContext(ActorKind.USER, marker, Instant.now(), marker)).execution();
        instances.add(start.instance().id());
        return start;
    }
    private List<WorkflowTaskView> tasks(long instance) { return reads.listTasks(instance, 0, 100).items(); }
    private void insertEffect(WorkflowExecutionResult start, UUID commandId, long quantity) {
        var instance = start.instance();
        effects.insert(new ServiceActionContext(commandId, instance.id(), instance.definitionVersionId(), start.activity().nodeKey(),
                instance.businessType(), instance.businessId(), admin()), quantity);
    }


    private io.github.leontecristian.workflow.core.dto.runtime.ReassignWorkflowTask move(WorkflowExecutionResult start, TaskAssignmentTarget target) {
        return new io.github.leontecristian.workflow.core.dto.runtime.ReassignWorkflowTask(UUID.randomUUID(), start.instance().id(), start.task().id(),
                version(start.instance().id()), target, " exact reason \n <plain> ");
    }
    private WorkflowCommandResult reassign(io.github.leontecristian.workflow.core.dto.runtime.ReassignWorkflowTask command) {
        return tx(() -> execution.reassignTask(command, admin()));
    }
    @Test void kindChangesPreserveCreationInputsIdentityAndHistoricalReceipts() throws Exception {
        publish(false); var start = start(); long instance = start.instance().id();
        var oldClaim = new ClaimWorkflowTask(UUID.randomUUID(), instance, start.task().id(), version(instance));
        var claimed = tx(() -> execution.claimTask(oldClaim, reviewer()));
        String original = jdbc.queryForObject("SELECT details_json FROM wf.workflow_history WHERE task_id=? AND event_type='TASK_CREATED'", String.class, start.task().id());
        var inputs = reads.taskInputs(instance, start.task().id());
        WorkflowCommandResult first = null;
        for (var target : List.of(new TaskAssignmentTarget(AssignmentTargetKind.USER, "reassign.reviewer2"),
                new TaskAssignmentTarget(AssignmentTargetKind.USER, "reassign.reviewer"),
                new TaskAssignmentTarget(AssignmentTargetKind.GROUP, "sandbox.finance"),
                new TaskAssignmentTarget(AssignmentTargetKind.ROLE, "sandbox.approver"))) {
            var command = move(start, target); var result = reassign(command);
            if (first == null) first = result;
            var task = reads.findTask(start.task().id()).orElseThrow();
            assertThat(task.assignment()).isEqualTo(target);
            assertThat(task.assigneeRef()).isEqualTo(target.kind() == AssignmentTargetKind.USER ? target.ref() : null);
            assertThat(task.taskKey()).isEqualTo(start.task().taskKey());
            assertThat(task.createdAt()).isEqualTo(start.task().createdAt());
            assertThat(reads.taskInputs(instance, task.id())).isEqualTo(inputs);
            assertThat(reassign(command).execution()).isEqualTo(result.execution());
            assertThat(jdbc.queryForObject("SELECT details_json FROM wf.workflow_history WHERE task_id=? AND event_type='TASK_CREATED'", String.class, task.id())).isEqualTo(original);
        }
        assertThat(tx(() -> execution.findReplay(oldClaim, reviewer())).orElseThrow()).isEqualTo(claimed.execution());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_history WHERE instance_id=? AND event_type='TASK_REASSIGNED'", Integer.class, instance)).isEqualTo(4);
        var history = jdbc.queryForObject("SELECT TOP 1 details_json FROM wf.workflow_history WHERE instance_id=? AND event_type='TASK_REASSIGNED' ORDER BY sequence_number", String.class, instance);
        assertThat(json.readTree(history).path("reason").asText()).isEqualTo(" exact reason \n <plain> ");
        assertThat(json.readTree(history).size()).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT MIN(details_schema_version) FROM wf.workflow_history WHERE instance_id=? AND event_type='TASK_REASSIGNED'", Integer.class, instance)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_command_receipt WHERE instance_id=? AND operation_type='REASSIGN_TASK' AND result_schema_version=8", Integer.class, instance)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_test.workflow_event_outbox WHERE instance_id=? AND event_type='TASK_REASSIGNED'", Integer.class, instance)).isEqualTo(4);
        assertThat(first.execution().task().assignment().kind()).isEqualTo(AssignmentTargetKind.USER);
    }
    @Test void reassignedUserCompletesAllWithFrozenSnapshotAndOriginalAttribution() throws Exception {
        publish(true); var start = start(); var before = tasks(start.instance().id());
        var inputs = reads.taskInputs(start.instance().id(), start.task().id());
        reassign(move(start, new TaskAssignmentTarget(AssignmentTargetKind.USER, "reassign.reviewer2")));
        assertThat(tasks(start.instance().id())).extracting(WorkflowTaskView::id).containsExactlyElementsOf(before.stream().map(WorkflowTaskView::id).toList());
        assertThat(reads.taskInputs(start.instance().id(), start.task().id())).isEqualTo(inputs);
        var complete = completion(start);
        tx(() -> execution.completeTask(complete, actor("reassign.reviewer2")));
        assertThat(reads.findTaskSubmission(start.task().id()).orElseThrow().actorRef()).isEqualTo("reassign.reviewer2");
        assertThat(reads.findActivity(start.activity().id()).orElseThrow().status()).isEqualTo(ActivityStatus.ACTIVE);
        failure(() -> reassign(move(start, new TaskAssignmentTarget(AssignmentTargetKind.GROUP, "sandbox.finance"))), Category.INVALID_STATE);
    }
    @Test void permissionTargetNoopAndRecorderFailuresLeaveNoWritesAndReplaySurvivesClosure() throws Exception {
        publish(false); var start = start(); var command = move(start, new TaskAssignmentTarget(AssignmentTargetKind.USER, "reassign.reviewer2"));
        long initial = version(start.instance().id());
        when(reassignment.canReassign(any())).thenReturn(false);
        failure(() -> reassign(command), Category.REASSIGNMENT_FORBIDDEN);
        when(reassignment.canReassign(any())).thenReturn(true);
        when(reassignment.isValidTarget(any(), any())).thenReturn(false);
        failure(() -> reassign(command), Category.INVALID_ASSIGNMENT);
        when(reassignment.isValidTarget(any(), any())).thenReturn(true);
        failure(() -> reassign(move(start, start.task().assignment())), Category.CONFLICT);
        doAnswer(invocation -> { throw new WorkflowExecutionException(Category.HOST_CONFIGURATION); }).when(target(recorder)).record(any());
        failure(() -> reassign(command), Category.HOST_CONFIGURATION);
        reset(target(recorder));
        assertThat(version(start.instance().id())).isEqualTo(initial);
        assertThat(reads.findTask(start.task().id()).orElseThrow()).isEqualTo(start.task());
        var accepted = reassign(command);
        cancel(command(start.instance().id()));
        when(reassignment.canReassign(any())).thenReturn(false);
        when(reassignment.isValidTarget(any(), any())).thenReturn(false);
        assertThat(reassign(command).execution()).isEqualTo(accepted.execution());
        when(reassignment.canAccess(any())).thenReturn(false);
        failure(() -> reassign(command), Category.NOT_FOUND);
    }
    @Test void administrationFiltersUseCurrentExactStateAndPreserveDeniedContinuation() throws Exception {
        publish(false); var first = start(); var second = start();
        reassign(move(first, new TaskAssignmentTarget(AssignmentTargetKind.USER, "reassign.reviewer2")));
        long firstId = first.task().id(), secondId = second.task().id();
        var page = reads.listAdministrationTasks(new io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskAdministrationQuery(
                TaskStatus.OPEN, "SANDBOX_REVIEW", null, null, null, null, firstId - 1, 1), item -> false);
        assertThat(page.items()).isEmpty(); assertThat(page.nextAfterId()).isEqualTo(firstId);
        var next = reads.listAdministrationTasks(new io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskAdministrationQuery(
                TaskStatus.OPEN, "SANDBOX_REVIEW", AssignmentTargetKind.GROUP, "sandbox.finance", null, null, firstId, 100),
                item -> item.task().id() == secondId);
        assertThat(next.items()).extracting(item -> item.task().id()).containsExactly(secondId);
        var exact = reads.listAdministrationTasks(new io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskAdministrationQuery(
                TaskStatus.OPEN, "SANDBOX_REVIEW", AssignmentTargetKind.USER, "reassign.reviewer2", "reassign.reviewer2", first.instance().id(), 0, 50), item -> true);
        assertThat(exact.items()).extracting(item -> item.task().id()).containsExactly(firstId);
        assertThat(exact.items().get(0).nodeName()).isNotBlank();
        assertThat(exact.items().get(0).taskTypeCode()).isEqualTo("SANDBOX_REVIEW");
        var caseMismatch = reads.listAdministrationTasks(new io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskAdministrationQuery(
                TaskStatus.OPEN, "SANDBOX_REVIEW", AssignmentTargetKind.USER, "Reassign.reviewer2", null, first.instance().id(), 0, 50), item -> true);
        assertThat(caseMismatch.items()).isEmpty();
        cancel(command(first.instance().id()));
        assertThat(reads.listAdministrationTasks(new io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskAdministrationQuery(
                TaskStatus.OPEN, null, null, null, null, first.instance().id(), 0, 50), item -> true).items()).isEmpty();
        assertThat(reads.listAdministrationTasks(new io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskAdministrationQuery(
                TaskStatus.CANCELLED, null, null, null, null, first.instance().id(), 0, 50), item -> true).items()).hasSize(1);
    }
    @Test void administrationReadsRetainedNamesWithoutRequiringExecutableCanonicalization() throws Exception {
        publish(false); var start = start();
        String original = jdbc.queryForObject("SELECT canonicalization_version FROM wf.workflow_definition_version WHERE id=?", String.class, versionId);
        try {
            jdbc.update("UPDATE wf.workflow_definition_version SET canonicalization_version=? WHERE id=?", "retained-test-format", versionId);
            assertThatThrownBy(() -> pinnedDefinitions.loadPinned(versionId))
                    .isInstanceOfSatisfying(io.github.leontecristian.workflow.core.exception.definition.WorkflowDefinitionException.class,
                            failure -> assertThat(failure.category()).isEqualTo(
                                    io.github.leontecristian.workflow.core.exception.definition.WorkflowDefinitionException.Category.UNSUPPORTED_CANONICALIZATION));
            var page = reads.listAdministrationTasks(new io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskAdministrationQuery(
                    TaskStatus.OPEN, null, null, null, null, null, start.task().id() - 1, 50), item -> true);
            assertThat(page.items()).anySatisfy(item -> {
                assertThat(item.task().id()).isEqualTo(start.task().id());
                assertThat(item.nodeName()).isNotBlank();
                assertThat(item.taskTypeCode()).isEqualTo("SANDBOX_REVIEW");
            });
        } finally {
            jdbc.update("UPDATE wf.workflow_definition_version SET canonicalization_version=? WHERE id=?", original, versionId);
        }
    }
    @Test void refreshedPolicyRejectsAHostCachedOriginalAssignment() throws Exception {
        publish(false); var start = start();
        when(reassignment.canReassign(any())).thenAnswer(invocation -> {
            var request = (io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext) invocation.getArgument(0);
            return request.task().assignment().kind() == AssignmentTargetKind.GROUP;
        });
        var pool = Executors.newSingleThreadExecutor();
        try {
            failure(() -> tx(() -> {
                // A host may have loaded this task earlier in its participating transaction.
                entityManager.find(io.github.leontecristian.workflow.core.entity.runtime.WorkflowTaskEntity.class, start.task().id());
                var winner = pool.submit(() -> reassign(move(start, new TaskAssignmentTarget(AssignmentTargetKind.USER, "reassign.reviewer2"))));
                try { winner.get(20, TimeUnit.SECONDS); }
                catch (Exception failed) { throw new IllegalStateException(failed); }
                long currentToken = jdbc.queryForObject("SELECT lock_version FROM wf.workflow_instance WHERE id=?", Long.class, start.instance().id());
                return execution.reassignTask(new io.github.leontecristian.workflow.core.dto.runtime.ReassignWorkflowTask(UUID.randomUUID(),
                        start.instance().id(), start.task().id(), currentToken,
                        new TaskAssignmentTarget(AssignmentTargetKind.ROLE, "sandbox.approver"), "cached grant must not survive"), admin());
            }), Category.REASSIGNMENT_FORBIDDEN);
            assertThat(reads.findTask(start.task().id()).orElseThrow().assignment())
                    .isEqualTo(new TaskAssignmentTarget(AssignmentTargetKind.USER, "reassign.reviewer2"));
        } finally { pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }
    @ParameterizedTest @ValueSource(strings={"completion", "claim", "cancellation"})
    void winningReassignmentPreventsStaleMutation(String operation) throws Exception { race(operation, true); }
    @ParameterizedTest @ValueSource(strings={"completion", "claim", "cancellation"})
    void winningMutationPreventsStaleReassignment(String operation) throws Exception { race(operation, false); }
    private void race(String operation, boolean moveWins) throws Exception {
        publish(false); var start = start(); long instance = start.instance().id(); long token = version(instance);
        var move = move(start, new TaskAssignmentTarget(AssignmentTargetKind.USER, "reassign.reviewer2"));
        var complete = completion(start); var cancel = command(instance);
        Supplier<Object> other = () -> switch(operation) {
            case "completion" -> execution.completeTask(complete, reviewer());
            case "claim" -> execution.claimTask(new ClaimWorkflowTask(UUID.randomUUID(), instance, start.task().id(), token), reviewer());
            default -> execution.cancelWorkflow(cancel, admin());
        };
        Supplier<Object> moving = () -> execution.reassignTask(move, admin());
        var claimed = new CountDownLatch(1); var release = new CountDownLatch(1); var entered = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var winner = pool.submit(() -> tx(() -> { var result = (moveWins ? moving : other).get(); claimed.countDown(); await(release); return result; }));
            assertThat(claimed.await(10, TimeUnit.SECONDS)).isTrue();
            var loser = pool.submit(() -> { entered.countDown(); try { return tx((moveWins ? other : moving)::get); } catch (WorkflowExecutionException failure) { return failure.category(); } });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> loser.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); winner.get(20, TimeUnit.SECONDS);
            assertThat(loser.get(20, TimeUnit.SECONDS)).isIn(Category.STALE_VERSION, Category.CONCURRENT_COMMAND);
            var task = reads.findTask(start.task().id()).orElseThrow();
            if (moveWins) { assertThat(task.status()).isEqualTo(TaskStatus.OPEN); assertThat(task.assignment()).isEqualTo(move.assignment()); }
            else assertThat(task.assignment()).isEqualTo(start.task().assignment());
        } finally { release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("test latch timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }

    @AfterEach void cleanupOwnedFixtures() {
        reset(target(recorder), target(resolver), target(ownership));
        tx(() -> {

            for (long instance : instances) {
                jdbc.update("DELETE FROM wf_test.workflow_event_inbox WHERE event_id IN (SELECT event_id FROM wf_test.workflow_event_outbox WHERE instance_id=?)", instance);
                jdbc.update("DELETE FROM wf_test.workflow_event_outbox WHERE instance_id=?", instance);
                jdbc.update("DELETE FROM wf_test.service_effect WHERE instance_id=?", instance);
                jdbc.update("DELETE FROM wf.workflow_command_receipt WHERE instance_id=?", instance);
                jdbc.update("DELETE FROM wf.workflow_history WHERE instance_id=?", instance);
                jdbc.update("DELETE FROM wf.workflow_variable WHERE instance_id=?", instance);
                jdbc.update("DELETE FROM wf.workflow_task WHERE activity_id IN (SELECT id FROM wf.workflow_activity WHERE instance_id=?)", instance);
                jdbc.update("DELETE FROM wf.workflow_activity WHERE instance_id=?", instance);
                jdbc.update("DELETE FROM wf.workflow_instance WHERE id=?", instance);
            }
            jdbc.update("DELETE FROM wf_test.sandbox_request WHERE name=? AND created_by=?", marker, marker);
            if (versionId != null) jdbc.update("DELETE FROM wf.workflow_definition_version WHERE id=?", versionId);
            if (familyId != null) jdbc.update("DELETE FROM wf.workflow_definition WHERE id=? AND workflow_key=?", familyId, marker);
            if (ownedTaskType != null) jdbc.update("DELETE FROM wf.workflow_task_type WHERE id=? AND code='SANDBOX_REVIEW'", ownedTaskType);
            return null;
        });
    }
}
