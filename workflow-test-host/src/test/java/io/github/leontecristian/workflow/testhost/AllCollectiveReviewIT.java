package io.github.leontecristian.workflow.testhost;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leontecristian.workflow.core.dto.definition.CheckWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionManagementContext;
import io.github.leontecristian.workflow.core.dto.definition.ImportWorkflowDraft;
import io.github.leontecristian.workflow.core.dto.runtime.ActivityStatus;
import io.github.leontecristian.workflow.core.dto.runtime.AllAssignmentResult;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.ClaimWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.CompletionPolicy;
import io.github.leontecristian.workflow.core.dto.runtime.InstanceStatus;
import io.github.leontecristian.workflow.core.dto.runtime.ResolvedTask;
import io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget;
import io.github.leontecristian.workflow.core.dto.runtime.TaskClosureReason;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowExecutionResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistorySummary;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.dto.runtime.ActionField;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionContext;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionDescriptor;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionResult;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
import io.github.leontecristian.workflow.core.dto.tasktype.CreateTaskType;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.runtime.AllTaskAssignmentResolver;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowEventRecorder;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowServiceAction;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import io.github.leontecristian.workflow.testhost.configuration.integration.DatabaseIntegrationTest;
import io.github.leontecristian.workflow.testhost.dto.runtime.SandboxStart;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxServiceEffectStore;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeTransaction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Committed ALL boundaries using the guarded test database and real sandbox adapters. */
@SpringBootTest(classes = WorkflowTestHostApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"workflow.console.enabled=true", "workflow.console.capabilities=sample",
                "workflow.console.admin.username=f021-admin", "workflow.console.admin.password=f021-local-test-password",
                "workflow.console.reviewer.username=f021-reviewer", "workflow.console.reviewer.password=f021-local-test-password"})
@Import(AllCollectiveReviewIT.CapacityAdapters.class)
class AllCollectiveReviewIT extends DatabaseIntegrationTest {
    @Autowired WorkflowDefinitionService definitions;
    @Autowired TaskTypeCatalogService catalog;
    @Autowired WorkflowRuntimeQueryService reads;
    @Autowired ConsoleRuntimeTransaction runtime;
    @Autowired SandboxRequestStore requests;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired WebApplicationContext web;
    @MockitoSpyBean WorkflowEventRecorder recorder;
    @MockitoSpyBean(name = "sandboxAllResolver") AllTaskAssignmentResolver resolver;
    @LocalServerPort int port;
    private final ObjectMapper json = new ObjectMapper();
    private final String marker = "f021.it." + UUID.randomUUID();
    private final List<Long> instances = new ArrayList<>();
    private Long familyId, versionId, ownedTaskType;
    private TransactionTemplate tx() { var result = new TransactionTemplate(transactions); result.setTimeout(30); return result; }
    private WorkflowCommandContext actor(String actor) { return new WorkflowCommandContext(ActorKind.USER, actor, Instant.now(), marker); }
    private long version() { return reads.findInstance(instances.get(instances.size() - 1)).orElseThrow().lockVersion(); }

    private ObjectNode fixture() throws Exception {
        Path path = Path.of("docs/features/F021-all-collective-review/sandbox-all-review.json");
        if (!Files.exists(path)) path = Path.of("..").resolve(path);
        var source = (ObjectNode) json.readTree(Files.readString(path)); source.put("key", marker); return source;
    }
    private void publish(ObjectNode source) {
        if (catalog.findByCode("SANDBOX_REVIEW").isEmpty()) {
            ownedTaskType = tx().execute(t -> catalog.create(new CreateTaskType("SANDBOX_REVIEW", marker, null, true)).after().id());
        }
        tx().executeWithoutResult(t -> {
            var draft = definitions.importDraft(new ImportWorkflowDraft(source.toString()), new DefinitionManagementContext(marker, Instant.now())).after();
            versionId = draft.id(); familyId = draft.definitionId();
            var checked = definitions.publish(new CheckWorkflowDefinition(versionId, draft.lockVersion()), new DefinitionManagementContext(marker, Instant.now()));
            assertThat(checked.published()).withFailMessage(() -> checked.report().toString()).isTrue();
        });
    }
    private WorkflowExecutionResult start() {
        long requestId = tx().execute(t -> requests.create(marker, "{\"amount\":9007199254740993.00}", marker, Instant.now())).id();
        var result = runtime.start(new SandboxStart(UUID.randomUUID(), versionId, requestId, 0), actor("f021-admin")).execution();
        instances.add(result.instance().id()); return result;
    }
    private List<WorkflowTaskView> tasks(long instance) {
        var result = new ArrayList<WorkflowTaskView>(); long cursor = 0;
        while (true) {
            var page = reads.listTasks(instance, cursor, 100); result.addAll(page.items());
            if (page.nextAfterId() == null) return result;
            cursor = page.nextAfterId();
        }
    }
    private WorkflowTaskView task(long instance, String key) { return tasks(instance).stream().filter(t -> t.taskKey().equals(key)).findFirst().orElseThrow(); }
    private CompleteWorkflowTask command(long instance, long taskId, String outcome) {
        return new CompleteWorkflowTask(UUID.randomUUID(), instance, taskId, reads.findInstance(instance).orElseThrow().lockVersion(),
                outcome, "REJECT".equals(outcome) ? "independent rejection" : null, CompleteWorkflowTask.parseResult("{\"note\":\"" + taskId + "\"}"));
    }
    private WorkflowCommandResult complete(CompleteWorkflowTask command) { return runtime.complete(command, actor("f021-reviewer")); }
    private int count(String table, long instance) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE instance_id=?", Integer.class, instance); }
    private String output(long instance) { return jdbc.queryForObject("SELECT json_value FROM wf.workflow_variable WHERE instance_id=? AND variable_key='reviewCollection'", String.class, instance); }
    private void failure(Runnable operation, Category category) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(WorkflowExecutionException.class, e -> assertThat(e.category()).isEqualTo(category));
    }
    private <T> T asReviewer(java.util.function.Supplier<T> action) {
        var previous = SecurityContextHolder.getContext(); var current = SecurityContextHolder.createEmptyContext();
        current.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("f021-reviewer", "unused", List.of(new SimpleGrantedAuthority("ROLE_CONSOLE_REVIEWER"))));
        SecurityContextHolder.setContext(current);
        try { return action.get(); } finally { SecurityContextHolder.setContext(previous); }
    }

    @Test void independentContributionsResolveOnceAndReceiptsRemainFrozen() throws Exception {
        publish(fixture()); var started = start(); long instance = started.instance().id();
        assertThat(started.activity().completionPolicy()).isEqualTo(CompletionPolicy.ALL);
        assertThat(started.activity().contributionOutcomeKey()).isEqualTo("APPROVE");
        assertThat(started.activity().requiredTaskCount()).isEqualTo(3);
        assertThat(tasks(instance)).extracting(WorkflowTaskView::taskKey).containsExactly("DIRECT", "FINANCE", "ROLE");
        assertThat(tasks(instance)).extracting(t -> t.assignment().kind()).containsExactly(AssignmentTargetKind.USER, AssignmentTargetKind.GROUP, AssignmentTargetKind.ROLE);
        assertThat(started.task().taskKey()).isEqualTo("DIRECT");
        var staleSibling = command(instance, task(instance, "FINANCE").id(), "APPROVE");
        var direct = command(instance, started.task().id(), "APPROVE");
        var waiting = complete(direct).execution();
        assertThat(waiting.task().id()).isEqualTo(started.task().id());
        assertThat(waiting.task().status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(waiting.activity().status()).isEqualTo(ActivityStatus.ACTIVE);
        assertThat(waiting.instance().currentNodeKey()).isEqualTo("review");
        assertThat(reads.listHistory(instance, waiting.firstHistorySequence() - 1, 100).items()).extracting(WorkflowHistorySummary::eventType).containsExactly("TASK_COMPLETED");
        assertThat(reads.listActivitySubmissions(started.activity().id()).items()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_variable WHERE instance_id=? AND variable_key='reviewCollection'", Integer.class, instance)).isZero();
        assertThat(count("wf_test.service_effect", instance)).isZero();
        failure(() -> complete(staleSibling), Category.STALE_VERSION);
        long later = task(instance, "ROLE").id();
        var claimed = asReviewer(() -> runtime.claim(new ClaimWorkflowTask(UUID.randomUUID(), instance, later, version()), actor("f021-reviewer"))).execution();
        assertThat(claimed.task().id()).isEqualTo(later);
        assertThat(claimed.task().assigneeRef()).isEqualTo("f021-reviewer");
        complete(command(instance, task(instance, "FINANCE").id(), "APPROVE"));
        var resolved = complete(command(instance, later, "APPROVE")).execution();
        assertThat(resolved.task().id()).isEqualTo(later);
        assertThat(resolved.activity().status()).isEqualTo(ActivityStatus.COMPLETED);
        assertThat(resolved.instance().status()).isEqualTo(InstanceStatus.COMPLETED);
        assertThat(resolved.instance().resultKey()).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT quantity FROM wf_test.service_effect WHERE instance_id=?", Long.class, instance)).isEqualTo(3L);
        var collection = json.readTree(output(instance));
        assertThat(collection.size()).isEqualTo(3);
        assertThat(collection.get(2).path("taskId").asText()).isEqualTo(Long.toString(later));
        assertThat(collection.get(0).path("comment").isNull()).isTrue();
        assertThat(collection.get(2).path("result").path("note").asText()).isEqualTo(Long.toString(later));
        assertThat(runtime.replayComplete(direct, actor("f021-reviewer")).orElseThrow().execution()).isEqualTo(waiting);
        assertThat(complete(direct).execution()).isEqualTo(waiting);
        assertThat(count("wf_test.service_effect", instance)).isEqualTo(1);
        assertThat(count("wf.workflow_command_receipt", instance)).isEqualTo(5); // start, three answers and later-task claim
    }

    @Test void laterSiblingReadsOneRetainedSnapshotAndGenericHistoryCannotExposeIt() throws Exception {
        publish(fixture()); var started = start(); long instance = started.instance().id();
        var expected = reads.taskInputs(instance, started.task().id());
        assertThat(((WorkflowJsonValue) expected.get("request").value()).toJson()).contains("9007199254740993.00");
        for (var task : tasks(instance)) assertThat(reads.taskInputs(instance, task.id())).isEqualTo(expected);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_history WHERE instance_id=? AND event_type='ACTIVITY_INPUTS_CAPTURED'", Integer.class, instance)).isEqualTo(1);
        var history = reads.listHistory(instance, 0, 100).items();
        var snapshot = history.stream().filter(h -> h.eventType().equals("ACTIVITY_INPUTS_CAPTURED")).findFirst().orElseThrow();
        assertThat(reads.findHistoryEvent(instance, snapshot.historySequence(), h -> true)).isEmpty();
        for (var created : history.stream().filter(h -> h.eventType().equals("TASK_CREATED")).toList()) {
            assertThat(created.detailsSchemaVersion()).isEqualTo((short) 6);
            var compact = reads.findHistoryEvent(instance, created.historySequence()).orElseThrow();
            assertThat(json.readTree(compact.detailsJson()).has("inputs")).isFalse();
            assertThat(reads.findHistoryEvent(instance, created.historySequence(), h -> h.taskId().equals(created.taskId()))).isPresent();
        }
        tx().executeWithoutResult(t -> jdbc.update("UPDATE wf.workflow_variable SET json_value=N'{\"changed\":true}' WHERE instance_id=? AND variable_key='requestData'", instance));
        assertThat(reads.taskInputs(instance, task(instance, "ROLE").id())).isEqualTo(expected);
    }

    @Test void earlyResolutionCancelsOnlyOpenSiblingsAndRetainsOriginalAcceptedFacts() throws Exception {
        publish(fixture()); var started = start(); long instance = started.instance().id();
        var contribution = complete(command(instance, started.task().id(), "APPROVE")).execution();
        if (Boolean.getBoolean("f021.browserHold")) browserHold(contribution, "waiting");
        long decisive = task(instance, "ROLE").id(), cancelled = task(instance, "FINANCE").id();
        var resolved = complete(command(instance, decisive, "REJECT")).execution();
        assertThat(resolved.instance().resultKey()).isEqualTo("REJECTED");
        var closed = reads.findTask(cancelled).orElseThrow();
        assertThat(closed.status()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(closed.closureReason()).isEqualTo(TaskClosureReason.ACTIVITY_RESOLVED);
        assertThat(closed.outcomeKey()).isNull(); assertThat(reads.findTaskSubmission(cancelled)).isEmpty();
        assertThat(reads.findTaskSubmission(started.task().id()).orElseThrow().outcomeKey()).isEqualTo("APPROVE");
        assertThat(reads.listHistory(instance, contribution.lastHistorySequence(), 100).items()).extracting(WorkflowHistorySummary::eventType)
                .startsWith("TASK_COMPLETED", "TASK_CANCELLED", "ACTIVITY_COMPLETED", "VARIABLES_WRITTEN");
        assertThat(json.readTree(output(instance)).size()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT quantity FROM wf_test.service_effect WHERE instance_id=?", Long.class, instance)).isEqualTo(2L);
        failure(() -> complete(command(instance, cancelled, "APPROVE")), Category.INVALID_STATE);
        failure(() -> asReviewer(() -> runtime.claim(new ClaimWorkflowTask(UUID.randomUUID(), instance, cancelled, version()), actor("f021-reviewer"))), Category.INVALID_STATE);
        if (Boolean.getBoolean("f021.browserHold")) browserHold(resolved, "resolved");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void recorderFailureRollsBackOnlyResolvingCommandIncludingOutputEffectsAndCancellations(boolean early) throws Exception {
        publish(fixture()); var started = start(); long instance = started.instance().id();
        complete(command(instance, started.task().id(), "APPROVE"));
        if (!early) complete(command(instance, task(instance, "FINANCE").id(), "APPROVE"));
        var before = reads.findInstance(instance).orElseThrow(); int history = count("wf.workflow_history", instance), receipts = count("wf.workflow_command_receipt", instance);
        var finalCommand = command(instance, task(instance, "ROLE").id(), early ? "REJECT" : "APPROVE");
        WorkflowEventRecorder target = AopTestUtils.getUltimateTargetObject(recorder);
        doAnswer(invocation -> { invocation.callRealMethod(); throw new IllegalStateException("F021 recorder rollback after downstream effect"); })
                .when(target).record(argThat(events -> events.stream().anyMatch(e -> e.eventType().equals("ACTIVITY_COMPLETED"))));
        try { assertThatThrownBy(() -> complete(finalCommand)).isInstanceOf(RuntimeException.class); }
        finally { reset(target); }
        assertThat(reads.findInstance(instance).orElseThrow()).isEqualTo(before);
        assertThat(reads.findTaskSubmission(finalCommand.taskId())).isEmpty();
        assertThat(reads.findTask(finalCommand.taskId()).orElseThrow().status()).isEqualTo(TaskStatus.OPEN);
        assertThat(tasks(instance)).noneMatch(t -> t.status() == TaskStatus.CANCELLED);
        assertThat(reads.findTaskSubmission(started.task().id())).isPresent();
        assertThat(count("wf.workflow_history", instance)).isEqualTo(history);
        assertThat(count("wf.workflow_command_receipt", instance)).isEqualTo(receipts);
        assertThat(count("wf_test.workflow_event_outbox", instance)).isEqualTo(history);
        assertThat(count("wf_test.service_effect", instance)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_variable WHERE instance_id=? AND variable_key='reviewCollection'", Integer.class, instance)).isZero();
        assertThat(complete(finalCommand).execution().instance().status()).isEqualTo(InstanceStatus.COMPLETED);
    }

    @Test void decisiveSiblingCommandsCompeteOnOneAggregateVersion() throws Exception {
        publish(fixture()); var started = start(); long instance = started.instance().id();
        var first = command(instance, started.task().id(), "REJECT");
        var second = command(instance, task(instance, "ROLE").id(), "REJECT");
        var ready = new CountDownLatch(2); var release = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> compete(first, ready, release)); var b = pool.submit(() -> compete(second, ready, release));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); release.countDown();
            var results = List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
            assertThat(results).filteredOn(WorkflowCommandResult.class::isInstance).hasSize(1);
            assertThat(results).filteredOn(result -> result == Category.STALE_VERSION || result == Category.CONCURRENT_COMMAND).hasSize(1);
            assertThat(tasks(instance)).filteredOn(t -> t.status() == TaskStatus.COMPLETED).hasSize(1);
            assertThat(tasks(instance)).filteredOn(t -> t.status() == TaskStatus.CANCELLED).hasSize(2);
            assertThat(count("wf_test.service_effect", instance)).isEqualTo(1);
            assertThat(count("wf.workflow_command_receipt", instance)).isEqualTo(2);
        } finally { release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }
    private Object compete(CompleteWorkflowTask command, CountDownLatch ready, CountDownLatch release) throws InterruptedException {
        ready.countDown(); release.await();
        try { return complete(command); } catch (WorkflowExecutionException failure) { return failure.category(); }
    }

    @ParameterizedTest @ValueSource(ints = {1, 100})
    void boundedRosterCreatesDeterministicTasksAndOneSnapshot(int count) throws Exception {
        var roster = new ArrayList<ResolvedTask>();
        for (int index = count - 1; index >= 0; index--) roster.add(new ResolvedTask("task" + String.format("%03d", index), new TaskAssignmentTarget(AssignmentTargetKind.USER, "f021-reviewer")));
        AllTaskAssignmentResolver target = AopTestUtils.getUltimateTargetObject(resolver);
        doReturn(new AllAssignmentResult(roster, "fixed-roster")).when(target).resolve(any());
        publish(fixture()); var started = start(); long instance = started.instance().id();
        assertThat(started.activity().requiredTaskCount()).isEqualTo(count);
        assertThat(started.task().taskKey()).isEqualTo("task000");
        assertThat(tasks(instance)).hasSize(count);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_history WHERE instance_id=? AND event_type='ACTIVITY_INPUTS_CAPTURED'", Integer.class, instance)).isEqualTo(1);
        if (count == 100) {
            var firstPage = reads.listTasks(instance, 0, 50); assertThat(firstPage.items()).hasSize(50);
            assertThat(reads.listTasks(instance, firstPage.nextAfterId(), 50).items()).hasSize(50);
        }
        assertThat(complete(command(instance, started.task().id(), count == 1 ? "APPROVE" : "REJECT")).execution().instance().status()).isEqualTo(InstanceStatus.COMPLETED);
        assertThat(tasks(instance)).filteredOn(t -> t.status() == TaskStatus.CANCELLED).hasSize(count - 1);
    }

    @ParameterizedTest @ValueSource(strings = {"empty", "oversized", "duplicate", "target"})
    void malformedRosterRollsBackAtomicEntryAndHostLink(String failure) throws Exception {
        AllTaskAssignmentResolver target = AopTestUtils.getUltimateTargetObject(resolver);
        doAnswer(invocation -> {
            var direct = new ResolvedTask("same", new TaskAssignmentTarget(AssignmentTargetKind.USER, "f021-reviewer"));
            return switch (failure) {
                case "empty" -> new AllAssignmentResult(List.of(), null);
                case "oversized" -> new AllAssignmentResult(java.util.Collections.nCopies(101, direct), null);
                case "duplicate" -> new AllAssignmentResult(List.of(direct, direct), null);
                case "target" -> new AllAssignmentResult(List.of(new ResolvedTask("bad", new TaskAssignmentTarget(null, "invalid"))), null);
                default -> throw new AssertionError(failure);
            };
        }).when(target).resolve(any());
        publish(fixture()); failure(this::start, Category.INVALID_ASSIGNMENT);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_instance WHERE definition_version_id=?", Integer.class, versionId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_test.sandbox_request WHERE name=? AND created_by=? AND instance_id IS NOT NULL", Integer.class, marker, marker)).isZero();
    }

    @Test void preservingMigrationIsAppliedAndHistoryConstraintIsTrusted() {
        var constraint = jdbc.queryForMap("SELECT definition,is_disabled,is_not_trusted FROM sys.check_constraints "
                + "WHERE parent_object_id=OBJECT_ID(N'wf.workflow_history') AND name='ck_workflow_history_json'");
        assertThat(constraint.get("definition").toString().toLowerCase(java.util.Locale.ROOT))
                .contains("isjson", "datalength", "25165824");
        assertThat(constraint.get("is_disabled")).isEqualTo(false);
        assertThat(constraint.get("is_not_trusted")).isEqualTo(false);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.DATABASECHANGELOG WHERE ID='006-all-history-capacity' "
                + "AND AUTHOR='workflow' AND FILENAME='db/changelog/workflow/changes/006-all-collective-review.xml' "
                + "AND EXECTYPE='EXECUTED' AND MD5SUM IS NOT NULL", Integer.class)).isEqualTo(1);
    }

    @Test void hundredTaskRosterRetainsOneEightMiBSnapshotAndLaterTaskCanReadAndComplete() throws Exception {
        AllTaskAssignmentResolver target = AopTestUtils.getUltimateTargetObject(resolver);
        var roster = new ArrayList<ResolvedTask>();
        for (int index = 0; index < 100; index++) roster.add(new ResolvedTask("large" + String.format("%03d", index),
                new TaskAssignmentTarget(AssignmentTargetKind.USER, "f021-reviewer")));
        doReturn(new AllAssignmentResult(roster, null)).when(target).resolve(any());
        var source = fixture();
        var service = source.withArray("nodes").addObject().put("key", "seedInputs").put("type", "SERVICE_TASK")
                .put("name", "Create large shared material").put("action", "f021.seedLargeInput");
        service.putObject("inputs");
        service.putObject("outputs").putObject("requestData").put("source", "actionOutput").put("key", "data");
        var transitions = source.withArray("transitions");
        for (var edge : transitions) if ("begin".equals(edge.path("key").asText())) ((ObjectNode) edge).put("to", "seedInputs");
        transitions.addObject().put("key", "enterReview").put("from", "seedInputs").put("to", "review");
        publish(source); var started = start(); long instance = started.instance().id();
        var last = tasks(instance).get(99);
        var input = (WorkflowJsonValue) reads.taskInputs(instance, last.id()).get("request").value();
        assertThat(input.toJson().length() * 2L).isEqualTo(8L * 1024 * 1024);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_history WHERE instance_id=? AND event_type='ACTIVITY_INPUTS_CAPTURED'", Integer.class, instance)).isEqualTo(1);
        // The hundred sibling facts retain references, rather than a hundred large snapshots.
        assertThat(jdbc.queryForObject("SELECT SUM(CONVERT(BIGINT,DATALENGTH(details_json))) FROM wf.workflow_history "
                + "WHERE instance_id=? AND event_type='TASK_CREATED'", Long.class, instance)).isLessThan(100L * 1024);
        assertThat(jdbc.queryForObject("SELECT DATALENGTH(details_json) FROM wf.workflow_history WHERE instance_id=? "
                + "AND event_type='ACTIVITY_INPUTS_CAPTURED'", Long.class, instance)).isBetween(8L * 1024 * 1024, 9L * 1024 * 1024);
        var mvc = MockMvcBuilders.webAppContextSetup(web).addFilters(web.getBean(FilterChainProxy.class)).build();
        var session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(
                "f021-reviewer", "unused", List.of(new SimpleGrantedAuthority("ROLE_CONSOLE_REVIEWER")))));
        var response = mvc.perform(get("/console/api/tasks/" + last.id() + "/contract").session(session)).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsByteArray().length).isBetween(4 * 1024 * 1024, 16 * 1024 * 1024);
        var accepted = complete(command(instance, last.id(), "APPROVE")).execution();
        assertThat(accepted.task().id()).isEqualTo(last.id());
        assertThat(accepted.activity().status()).isEqualTo(ActivityStatus.ACTIVE);
        assertThat(accepted.instance().currentNodeKey()).isEqualTo("review");
        assertThat(reads.taskInputs(instance, last.id()).get("request").value()).isEqualTo(input);
        assertThat(tasks(instance)).hasSize(100);
        assertThat(started.task().id()).isNotEqualTo(last.id());
    }

    private static WorkflowJsonValue largeSharedValue() {
        var source = new StringBuilder(4 * 1024 * 1024).append('[');
        int payload = 4 * 1024 * 1024 - 901; // two brackets, 299 commas and 600 quotes
        for (int index = 0; index < 300; index++) {
            if (index > 0) source.append(',');
            int length = index == 299 ? payload - 299 * 14000 : 14000;
            source.append('"').append("x".repeat(length)).append('"');
        }
        var value = WorkflowJsonValue.parse(source.append(']').toString());
        assertThat(value.toJson().length() * 2L).isEqualTo(8L * 1024 * 1024); return value;
    }

    /** Large results are generated, never placed in the bounded definition document. */
    private WorkflowJsonValue fullTaskResult() {
        String prefix = "{\"a\":\"" + "x".repeat(16000) + "\",\"b\":\"" + "x".repeat(16000) + "\",\"c\":\"";
        var result = CompleteWorkflowTask.parseResult(prefix + "x".repeat(32768 - prefix.length() - 2) + "\"}");
        assertThat(result.toJson().length() * 2).isEqualTo(64 * 1024); return result;
    }

    private ObjectNode capacityFixture(int copies) throws Exception {
        var source = fixture(); var nodes = source.withArray("nodes");
        for (var node : nodes) {
            if (!"review".equals(node.path("key").asText())) continue;
            var schema = ((ObjectNode) node.path("resultContract")).putObject("schema").put("type", "object").put("additionalProperties", false);
            var properties = schema.putObject("properties");
            for (String field : List.of("a", "b", "c")) properties.putObject(field).put("type", "string").put("maxLength", 16000);
            schema.putArray("required").add("a").add("b").add("c");
        }
        var follow = nodes.addObject().put("key", "followUp").put("type", "HUMAN_TASK").put("name", "Original collection input").put("taskType", "SANDBOX_REVIEW");
        follow.putObject("assignment").put("resolver", "sandbox.singleReviewer").putObject("inputs");
        follow.putObject("completion").put("policy", "SINGLE");
        follow.putObject("resultContract").put("mode", "FORBIDDEN");
        follow.putArray("outcomes").addObject().put("key", "COMPLETE").put("label", "Complete").put("requiresComment", false);
        follow.putObject("inputs").putObject("original").put("source", "submission").put("nodeKey", "review");
        for (int index = 0; index < copies; index++) {
            ObjectNode service = null;
            String key = index == 0 ? "recordApproved" : "copy" + index;
            if (index == 0) {
                for (var node : nodes) if (key.equals(node.path("key").asText())) service = (ObjectNode) node;
            } else service = nodes.addObject().put("key", key).put("type", "SERVICE_TASK").put("name", "Whole collection replacement");
            service.put("action", "f021.copyCollection");
            service.putObject("inputs").putObject("data").put("source", "variable").put("key", "reviewCollection");
            service.putObject("outputs").putObject("reviewCollection").put("source", "actionOutput").put("key", "data");
        }
        var edges = source.withArray("transitions");
        for (var edge : edges) if ("approved".equals(edge.path("key").asText())) {
            ((ObjectNode) edge).put("from", copies == 1 ? "recordApproved" : "copy" + (copies - 1)).put("to", "followUp");
        }
        for (int index = 1; index < copies; index++) edges.addObject().put("key", "copyEdge" + index)
                .put("from", index == 1 ? "recordApproved" : "copy" + (index - 1)).put("to", "copy" + index);
        edges.addObject().put("key", "finishFollowUp").put("from", "followUp").put("to", "APPROVED").put("outcome", "COMPLETE");
        return source;
    }

    /** Canonical control escaping supplies the remaining bytes without exceeding 2,000 comment characters. */
    private List<String> commentsForExactCollection(List<WorkflowTaskView> roster, WorkflowJsonValue result) {
        long base = 2 + roster.size() - 1;
        for (var task : roster) base += collectionEntry(task, "", result).length();
        long remaining = 4L * 1024 * 1024 - base;
        assertThat(remaining).isBetween(0L, 12000L * roster.size());
        var comments = new ArrayList<String>();
        for (int index = 0; index < roster.size(); index++) {
            int budget = (int) Math.min(12000, remaining);
            // At the very top of a slot use 2,000 controls; otherwise leave room for ASCII remainder.
            if (budget > 11995 && budget < 12000) budget = 11994;
            String comment = String.valueOf((char) 1).repeat(budget / 6) + "x".repeat(budget % 6);
            assertThat(comment.length()).isLessThanOrEqualTo(2000);
            comments.add(comment); remaining -= budget;
        }
        assertThat(remaining).isZero(); return comments;
    }
    private String collectionEntry(WorkflowTaskView task, String comment, WorkflowJsonValue result) {
        try {
            return "{\"taskId\":\"" + task.id() + "\",\"taskKey\":\"" + task.taskKey()
                    + "\",\"outcomeKey\":\"APPROVE\",\"comment\":" + json.writeValueAsString(comment) + ",\"result\":" + result.toJson() + "}";
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void exactEightMiBCollectionCrossesOutputReplacementAndHumanReadOrRollsBackExcessHistory(boolean excess) throws Exception {
        AllTaskAssignmentResolver target = AopTestUtils.getUltimateTargetObject(resolver);
        var roster = new ArrayList<ResolvedTask>();
        for (int index = 0; index < 100; index++) roster.add(new ResolvedTask("large" + String.format("%03d", index), new TaskAssignmentTarget(AssignmentTargetKind.USER, "f021-reviewer")));
        doReturn(new AllAssignmentResult(roster, null)).when(target).resolve(any());
        publish(capacityFixture(excess ? 3 : 1)); var started = start(); long instance = started.instance().id();
        var persisted = tasks(instance); var result = fullTaskResult(); var comments = commentsForExactCollection(persisted, result);
        for (int index = 0; index < 99; index++) complete(new CompleteWorkflowTask(UUID.randomUUID(), instance, persisted.get(index).id(),
                reads.findInstance(instance).orElseThrow().lockVersion(), "APPROVE", comments.get(index), result));
        var before = reads.findInstance(instance).orElseThrow(); int history = count("wf.workflow_history", instance);
        var last = new CompleteWorkflowTask(UUID.randomUUID(), instance, persisted.get(99).id(), before.lockVersion(), "APPROVE", comments.get(99), result);
        if (excess) {
            failure(() -> complete(last), Category.LIMIT_EXCEEDED);
            assertThat(reads.findInstance(instance).orElseThrow()).isEqualTo(before);
            assertThat(reads.findTaskSubmission(last.taskId())).isEmpty();
            assertThat(tasks(instance)).filteredOn(t -> t.status() == TaskStatus.COMPLETED).hasSize(99);
            assertThat(count("wf.workflow_history", instance)).isEqualTo(history);
            assertThat(count("wf.workflow_command_receipt", instance)).isEqualTo(100);
            assertThat(count("wf_test.service_effect", instance)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_variable WHERE instance_id=? AND variable_key='reviewCollection'", Integer.class, instance)).isZero();
            return;
        }
        var resolved = complete(last).execution();
        assertThat(resolved.instance().currentNodeKey()).isEqualTo("followUp");
        var next = tasks(instance).stream().filter(t -> t.status() == TaskStatus.OPEN).findFirst().orElseThrow();
        var original = (WorkflowJsonValue) reads.taskInputs(instance, next.id()).get("original").value();
        String mutable = output(instance);
        assertThat(original.toJson().length() * 2L).isEqualTo(8L * 1024 * 1024);
        assertThat(mutable.length()).isEqualTo(original.toJson().length());
        assertThat(mutable).isNotEqualTo(original.toJson());
        assertThat(count("wf_test.service_effect", instance)).isEqualTo(1);
        var retainedOutput = reads.listVariables(instance, null, 100, null).items().stream()
                .filter(value -> value.key().equals("reviewCollection")).findFirst().orElseThrow();
        assertThat(((WorkflowJsonValue) retainedOutput.value().value()).toJson()).isEqualTo(mutable);
        var mvc = MockMvcBuilders.webAppContextSetup(web).addFilters(web.getBean(FilterChainProxy.class)).build();
        var session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(
                "f021-reviewer", "unused", List.of(new SimpleGrantedAuthority("ROLE_CONSOLE_REVIEWER")))));
        var contract = mvc.perform(get("/console/api/tasks/" + next.id() + "/contract").session(session)).andReturn().getResponse();
        assertThat(contract.getStatus()).isEqualTo(200);
        assertThat(contract.getContentAsByteArray().length).isBetween(2 * 1024 * 1024, 16 * 1024 * 1024);
        var admin = new MockHttpSession();
        admin.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(
                "f021-admin", "unused", List.of(new SimpleGrantedAuthority("ROLE_CONSOLE_ADMIN")))));
        var variables = mvc.perform(get("/console/api/instances/" + instance + "/variables").session(admin)).andReturn().getResponse();
        assertThat(variables.getStatus()).isEqualTo(200);
        assertThat(variables.getContentAsByteArray().length).isBetween(2 * 1024 * 1024, 16 * 1024 * 1024);
        long largest = jdbc.queryForObject("SELECT TOP (1) sequence_number FROM wf.workflow_history WHERE instance_id=? ORDER BY DATALENGTH(details_json) DESC", Long.class, instance);
        var detail = mvc.perform(get("/console/api/instances/" + instance + "/history/" + largest).session(admin)).andReturn().getResponse();
        assertThat(detail.getStatus()).isEqualTo(200);
        assertThat(detail.getContentAsByteArray().length).isBetween(2 * 1024 * 1024, 40 * 1024 * 1024);
    }

    @TestConfiguration(proxyBeanMethods = false) static class CapacityAdapters {
        @Bean WorkflowServiceAction capacitySeedAction() {
            return new WorkflowServiceAction() {
                public ServiceActionDescriptor descriptor() {
                    return new ServiceActionDescriptor("f021.seedLargeInput", List.of(),
                            List.of(new ActionField("data", VariableValueType.JSON, true, false)));
                }
                public ServiceActionResult execute(ServiceActionContext context, Map<String, WorkflowValue> inputs) {
                    return new ServiceActionResult(Map.of("data", new WorkflowValue(VariableValueType.JSON, largeSharedValue())));
                }
            };
        }
        @Bean WorkflowServiceAction capacityCopyAction(SandboxServiceEffectStore effects) {
            return new WorkflowServiceAction() {
                public ServiceActionDescriptor descriptor() {
                    var field = new ActionField("data", VariableValueType.JSON, true, false);
                    return new ServiceActionDescriptor("f021.copyCollection", List.of(field), List.of(field));
                }
                public ServiceActionResult execute(ServiceActionContext context, Map<String, WorkflowValue> inputs) {
                    effects.insert(context, 1);
                    var original = (WorkflowJsonValue) inputs.get("data").value();
                    var replacement = WorkflowJsonValue.parse(original.toJson().replaceFirst("xxxxx", "yxxxx"));
                    return new ServiceActionResult(Map.of("data", new WorkflowValue(VariableValueType.JSON, replacement)));
                }
            };
        }
    }

    private void browserHold(WorkflowExecutionResult state, String phase) throws Exception {
        Path local = (Files.isDirectory(Path.of("docs")) ? Path.of("") : Path.of("..")).resolve(".local");
        Files.createDirectories(local);
        Files.writeString(local.resolve("f021-runtime.json"), json.writeValueAsString(Map.of(
                "port", port, "pid", ProcessHandle.current().pid(), "phase", phase,
                "instance", state.instance().id(), "activity", state.activity().id(),
                "task", task(state.instance().id(), "ROLE").id(), "version", versionId)));
        Path signal = local.resolve("f021-" + phase + "-browser.done");
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(20);
        while (!Files.exists(signal) && System.nanoTime() < deadline) Thread.sleep(250);
        assertThat(Files.exists(signal)).as("Focused browser observation completion signal: " + phase).isTrue();
        Files.deleteIfExists(signal);
    }

    @AfterEach void cleanupOwnedFixtures() {
        WorkflowEventRecorder recorderTarget = AopTestUtils.getUltimateTargetObject(recorder);
        AllTaskAssignmentResolver resolverTarget = AopTestUtils.getUltimateTargetObject(resolver);
        reset(recorderTarget); reset(resolverTarget);
        tx().executeWithoutResult(t -> {
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
        });
    }
}
