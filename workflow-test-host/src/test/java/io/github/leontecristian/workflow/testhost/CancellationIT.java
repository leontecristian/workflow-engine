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
import io.github.leontecristian.workflow.testhost.configuration.integration.DatabaseIntegrationTest;
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

/** Committed cancellation boundaries; the inherited initializer permits only the dedicated test database. */
@TestPropertySource(properties = {"workflow.console.enabled=true", "workflow.console.capabilities=sample",
        "workflow.console.admin.username=cancel.admin", "workflow.console.admin.password=integration-only-admin",
        "workflow.console.reviewer.username=cancel.reviewer", "workflow.console.reviewer.password=integration-only-reviewer"})
class CancellationIT extends DatabaseIntegrationTest {
    @Autowired WorkflowDefinitionService definitions;
    @Autowired TaskTypeCatalogService catalog;
    @Autowired WorkflowRuntimeQueryService reads;
    @Autowired WorkflowExecutionService execution;
    @Autowired ConsoleRuntimeTransaction runtime;
    @Autowired SandboxRequestStore requests;
    @Autowired SandboxServiceEffectStore effects;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean ConsoleWorkflowCancellationAuthorization cancellation;
    @MockitoSpyBean ConsoleTaskOwnershipAuthorization ownership;
    @MockitoSpyBean WorkflowEventRecorder recorder;
    @MockitoBean(name = "consoleCapabilities") DefinitionCapabilityProvider capabilities;
    @Autowired ConsoleSettings settings;
    @MockitoSpyBean(name = "sandboxAllResolver") AllTaskAssignmentResolver resolver;
    private final String marker = "cancel.it." + UUID.randomUUID();
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
    private WorkflowCommandContext admin() { return actor("cancel.admin"); }
    private WorkflowCommandContext reviewer() { return actor("cancel.reviewer"); }
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

    @Test void claimedSingleStopsWithExactHistoryAndDeliveryAndPreservesCommittedBusinessStatus() throws Exception {
        publish(false); var start = start(); long instance = start.instance().id();
        claim(start, version(instance));
        var claimed = reads.findTask(start.task().id()).orElseThrow();
        assertThat(claimed.assigneeRef()).isEqualTo("cancel.reviewer");
        var command = command(instance);
        doAnswer(invocation -> { throw new IllegalStateException("capabilities temporarily unavailable"); }).when(capabilities).snapshot();
        var accepted = cancel(command);
        assertThat(accepted.replayed()).isFalse();
        var result = accepted.execution();
        assertThat(result.instance().status()).isEqualTo(InstanceStatus.CANCELLED);
        assertThat(result.instance().terminationReason()).isEqualTo(command.reason());
        assertThat(result.instance().currentNodeKey()).isEqualTo(start.activity().nodeKey());
        assertThat(result.instance().resultKey()).isNull();
        assertThat(result.activity().status()).isEqualTo(ActivityStatus.CANCELLED);
        assertThat(result.activity().outcomeKey()).isNull();
        assertThat(result.cancelledTaskCount()).isEqualTo(1);
        var closed = reads.findTask(start.task().id()).orElseThrow();
        assertThat(closed.status()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(closed.closureReason()).isEqualTo(TaskClosureReason.INSTANCE_CANCELLED);
        assertThat(closed.assignment()).isEqualTo(claimed.assignment());
        assertThat(closed.assigneeRef()).isEqualTo(claimed.assigneeRef());
        assertThat(closed.closedAt()).isEqualTo(result.instance().endedAt()).isEqualTo(result.activity().closedAt());
        assertThat(closed.closedBy()).isEqualTo(result.instance().endedBy()).isEqualTo(result.activity().closedBy());
        var history = reads.listHistory(instance, result.firstHistorySequence() - 1, 100).items();
        assertThat(history).extracting(WorkflowHistorySummary::eventType).containsExactly("TASK_CANCELLED", "ACTIVITY_CANCELLED", "INSTANCE_CANCELLED");
        assertThat(history).allSatisfy(h -> assertThat(h.detailsSchemaVersion()).isEqualTo((short) 1));
        var detail = reads.findHistoryEvent(instance, result.lastHistorySequence()).orElseThrow();
        assertThat(json.readTree(detail.detailsJson())).isEqualTo(json.createObjectNode().put("reason", command.reason()));
        assertThat(jdbc.queryForObject("SELECT status FROM wf_test.sandbox_request WHERE instance_id=?", String.class, instance)).isEqualTo("IN_REVIEW");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_history WHERE instance_id=? AND event_type='SERVICE_TASK_COMPLETED'", Integer.class, instance)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT result_schema_version FROM wf.workflow_command_receipt WHERE command_id=?", Short.class, command.commandId().toString())).isEqualTo((short) 7);
        var payloads = jdbc.queryForList("SELECT event_json FROM wf_test.workflow_event_outbox WHERE command_id=? ORDER BY history_sequence", String.class, command.commandId().toString());
        assertThat(payloads).hasSize(3);
        assertThat(payloads).allSatisfy(p -> assertThat(p).doesNotContain(command.reason()));
        assertThat(json.readTree(payloads.get(2)).path("payload")).isEqualTo(json.createObjectNode().put("cancelledTaskCount", "1"));
        assertThat(cancel(command).execution()).isEqualTo(result);
        failure(() -> cancel(command(instance)), Category.INVALID_STATE);
        failure(() -> complete(completion(start)), Category.INVALID_STATE);
        failure(() -> claim(start, version(instance)), Category.INVALID_STATE);
    }

    @ParameterizedTest @ValueSource(ints = {3, 100})
    void partialAllRetainsAcceptedSubmissionInputsAndPriorEffectsWithoutProducingFinalOutput(int rosterSize) throws Exception {
        if (rosterSize == 100) {
            var roster = new ArrayList<ResolvedTask>();
            for (int i = 0; i < rosterSize; i++) roster.add(new ResolvedTask(String.format("TASK%03d", i), new TaskAssignmentTarget(AssignmentTargetKind.USER, "cancel.reviewer")));
            doReturn(new AllAssignmentResult(roster, "retained-revision")).when(target(resolver)).resolve(any());
        }
        publish(true); var start = start(); long instance = start.instance().id();
        var inputs = reads.taskInputs(instance, start.task().id());
        var contribution = complete(completion(start)).execution();
        tx(() -> { insertEffect(start, UUID.randomUUID(), 7); return null; });
        var result = cancel(command(instance)).execution();
        assertThat(result.cancelledTaskCount()).isEqualTo(rosterSize - 1);
        assertThat(result.lastHistorySequence() - result.firstHistorySequence() + 1).isEqualTo(rosterSize + 1);
        assertThat(tasks(instance)).filteredOn(t -> t.status() == TaskStatus.CANCELLED).hasSize(rosterSize - 1);
        assertThat(reads.findTask(start.task().id()).orElseThrow()).isEqualTo(contribution.task());
        assertThat(reads.findTaskSubmission(start.task().id())).isPresent();
        assertThat(reads.listActivitySubmissions(start.activity().id()).items()).hasSize(1);
        for (var mode : List.of(Mode.OPTIONAL, Mode.REQUIRED)) failure(() -> tx(() ->
                ReflectionTestUtils.invokeMethod(target(reads), "resolveSubmission", instance, start.activity().nodeKey(), mode)), Category.SUBMISSION_UNAVAILABLE);
        assertThat(reads.taskInputs(instance, start.task().id())).isEqualTo(inputs);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_variable WHERE instance_id=? AND variable_key='reviewCollection'", Integer.class, instance)).isZero();
        assertThat(count("wf_test.service_effect", instance)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT quantity FROM wf_test.service_effect WHERE instance_id=?", Long.class, instance)).isEqualTo(7L);
        assertThat(runtime.replayComplete(completionWithId(start, contribution.commandId()), reviewer()).orElseThrow().execution()).isEqualTo(contribution);
        var keys = jdbc.queryForList("SELECT t.task_key FROM wf.workflow_history h JOIN wf.workflow_task t ON t.id=h.task_id WHERE h.instance_id=? AND h.event_type='TASK_CANCELLED' ORDER BY h.sequence_number", String.class, instance);
        assertThat(keys).isSorted();
    }
    private CompleteWorkflowTask completionWithId(WorkflowExecutionResult start, UUID commandId) {
        return new CompleteWorkflowTask(commandId, start.instance().id(), start.task().id(), start.instance().lockVersion(), "APPROVE", null, CompleteWorkflowTask.parseResult("{\"note\":\"retained\"}"));
    }

    @Test void lookupAndMutationRequireCurrentAccessButReplayDoesNotRequireFreshPrivilege() throws Exception {
        publish(false); var start = start(); long instance = start.instance().id(); var command = command(instance);
        when(cancellation.canCancel(any())).thenReturn(false);
        failure(() -> cancel(command), Category.CANCELLATION_FORBIDDEN);
        assertThat(tx(() -> execution.findCancellationReplay(command, admin()))).isEmpty();
        assertThat(version(instance)).isEqualTo(command.expectedLockVersion());
        when(cancellation.canAccess(any())).thenReturn(false);
        failure(() -> cancel(command), Category.NOT_FOUND);
        failure(() -> tx(() -> execution.findCancellationReplay(command, admin())), Category.NOT_FOUND);
        when(cancellation.canAccess(any())).thenReturn(true);
        when(cancellation.canCancel(any())).thenReturn(true);
        var result = cancel(command).execution();
        when(cancellation.canCancel(any())).thenThrow(new IllegalStateException("must not consult privilege during replay"));
        assertThat(tx(() -> execution.findCancellationReplay(command, admin())).orElseThrow()).isEqualTo(result);
        assertThat(cancel(command).execution()).isEqualTo(result);
        failure(() -> tx(() -> execution.findCancellationReplay(command, reviewer())), Category.COMMAND_ID_CONFLICT);
        failure(() -> cancel(new CancelWorkflow(command.commandId(), instance, command.expectedLockVersion(), "different")), Category.COMMAND_ID_CONFLICT);
        when(cancellation.canAccess(any())).thenThrow(new IllegalStateException("private backend failure"));
        failure(() -> cancel(command), Category.AUTHORIZATION_LOOKUP_FAILED);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void callbackOrRecorderFailureRollsBackAllAttemptedWritesAndPreservesEarlierContribution(boolean recorderFailure) throws Exception {
        publish(true); var start = start(); long instance = start.instance().id();
        var prior = complete(completion(start)).execution();
        tx(() -> { insertEffect(start, UUID.randomUUID(), 7); return null; });
        int histories = count("wf.workflow_history", instance), receipts = count("wf.workflow_command_receipt", instance), events = count("wf_test.workflow_event_outbox", instance);
        var command = command(instance); var calls = new AtomicInteger();
        if (recorderFailure) doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("recorder failed after durable writes");
        }).when(target(recorder)).record(any());
        failure(() -> tx(() -> execution.cancelWorkflow(command, admin(), context -> {
            calls.incrementAndGet();
            assertThat(context.instance().status()).isEqualTo(InstanceStatus.ACTIVE);
            insertEffect(start, command.commandId(), 11);
            if (!recorderFailure) throw new IllegalStateException("host effect rejected");
        })), recorderFailure ? Category.HOST_CONFIGURATION : Category.CANCELLATION_EFFECT_FAILED);
        reset(target(recorder));
        assertThat(calls).hasValue(1);
        assertThat(reads.findInstance(instance).orElseThrow().status()).isEqualTo(InstanceStatus.ACTIVE);
        assertThat(version(instance)).isEqualTo(command.expectedLockVersion());
        assertThat(tasks(instance)).filteredOn(t -> t.status() == TaskStatus.OPEN).hasSize(2);
        assertThat(reads.findTask(start.task().id()).orElseThrow()).isEqualTo(prior.task());
        assertThat(count("wf.workflow_history", instance)).isEqualTo(histories);
        assertThat(count("wf.workflow_command_receipt", instance)).isEqualTo(receipts);
        assertThat(count("wf_test.workflow_event_outbox", instance)).isEqualTo(events);
        assertThat(count("wf_test.service_effect", instance)).isEqualTo(1);
        assertThat(tx(() -> execution.findCancellationReplay(command, admin()))).isEmpty();
        var accepted = tx(() -> execution.cancelWorkflow(command, admin(), context -> { calls.incrementAndGet(); insertEffect(start, command.commandId(), 11); }));
        assertThat(accepted.replayed()).isFalse();
        assertThat(cancel(command).replayed()).isTrue();
        assertThat(calls).hasValue(2);
        assertThat(count("wf_test.service_effect", instance)).isEqualTo(2);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void cancellationWinningClaimPreventsCompetingCompletionOrOwnership(boolean ownershipCommand) throws Exception {
        publish(false); var start = start(); long instance = start.instance().id();
        var command = command(instance); var complete = completion(start);
        var claimed = new CountDownLatch(1); var release = new CountDownLatch(1); var entered = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var winner = pool.submit(() -> tx(() -> execution.cancelWorkflow(command, admin(), context -> { claimed.countDown(); await(release); })));
            assertThat(claimed.await(10, TimeUnit.SECONDS)).isTrue();
            var loser = pool.submit(() -> {
                entered.countDown();
                try {
                    if (ownershipCommand) claim(start, command.expectedLockVersion()); else complete(complete);
                    return null;
                } catch (WorkflowExecutionException e) { return e.category(); }
            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> loser.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            assertThat(winner.get(20, TimeUnit.SECONDS).execution().instance().status()).isEqualTo(InstanceStatus.CANCELLED);
            assertThat(loser.get(20, TimeUnit.SECONDS)).isIn(Category.STALE_VERSION, Category.CONCURRENT_COMMAND);
            assertThat(reads.findTask(start.task().id()).orElseThrow().status()).isEqualTo(TaskStatus.CANCELLED);
        } finally { release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void completionOrOwnershipWinningClaimMakesCancellationStale(boolean ownershipCommand) throws Exception {
        publish(false, !ownershipCommand); var start = start(); long instance = start.instance().id();
        var command = command(instance); var complete = completion(start);
        var changed = new CountDownLatch(1); var release = new CountDownLatch(1); var entered = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var winner = pool.submit(() -> tx(() -> {
                var result = ownershipCommand ? execution.claimTask(new ClaimWorkflowTask(UUID.randomUUID(), instance, start.task().id(), command.expectedLockVersion()), reviewer())
                        : execution.completeTask(complete, reviewer());
                changed.countDown(); await(release); return result;
            }));
            assertThat(changed.await(10, TimeUnit.SECONDS)).isTrue();
            var loser = pool.submit(() -> { entered.countDown(); try { cancel(command); return null; } catch (WorkflowExecutionException e) { return e.category(); } });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> loser.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); winner.get(20, TimeUnit.SECONDS);
            assertThat(loser.get(20, TimeUnit.SECONDS)).isIn(Category.STALE_VERSION, Category.CONCURRENT_COMMAND);
            var retained = reads.findInstance(instance).orElseThrow();
            assertThat(retained.status()).isEqualTo(InstanceStatus.ACTIVE);
            assertThat(retained.currentNodeKey()).isEqualTo(ownershipCommand ? "REVIEW" : "SECOND_REVIEW");
            if (!ownershipCommand) assertThat(tasks(instance)).filteredOn(t -> t.status() == TaskStatus.OPEN).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_command_receipt WHERE command_id=?", Integer.class, command.commandId().toString())).isZero();
        } finally { release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test void duplicateCancellationCommitsOneEffectAndOneEventSetThenBothAttemptsRecover() throws Exception {
        publish(false); var start = start(); long instance = start.instance().id(); var command = command(instance);
        var claimed = new CountDownLatch(1); var release = new CountDownLatch(1); var entered = new CountDownLatch(1); var calls = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> tx(() -> execution.cancelWorkflow(command, admin(), context -> { calls.incrementAndGet(); claimed.countDown(); await(release); })));
            assertThat(claimed.await(10, TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> { entered.countDown(); try { return cancel(command); } catch (WorkflowExecutionException e) { assertThat(e.category()).isIn(Category.STALE_VERSION, Category.CONCURRENT_COMMAND); return null; } });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); var accepted = first.get(20, TimeUnit.SECONDS); second.get(20, TimeUnit.SECONDS);
            assertThat(calls).hasValue(1);
            assertThat(cancel(command).execution()).isEqualTo(accepted.execution());
            assertThat(tx(() -> execution.findCancellationReplay(command, admin())).orElseThrow()).isEqualTo(accepted.execution());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_command_receipt WHERE command_id=?", Integer.class, command.commandId().toString())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_history WHERE command_id=?", Integer.class, command.commandId().toString())).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_test.workflow_event_outbox WHERE command_id=?", Integer.class, command.commandId().toString())).isEqualTo(3);
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
