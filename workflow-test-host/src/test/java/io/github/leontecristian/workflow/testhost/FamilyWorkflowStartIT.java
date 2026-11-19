package io.github.leontecristian.workflow.testhost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leontecristian.workflow.core.dto.definition.CheckWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionManagementContext;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionStatus;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionVersionSummary;
import io.github.leontecristian.workflow.core.dto.definition.ImportWorkflowDraft;
import io.github.leontecristian.workflow.core.dto.definition.RetireWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.StartInputs;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandResult;
import io.github.leontecristian.workflow.core.dto.tasktype.CreateTaskType;
import io.github.leontecristian.workflow.core.entity.definition.WorkflowDefinitionVersionEntity;
import io.github.leontecristian.workflow.core.entity.definition.WorkflowDefinitionEntity;
import io.github.leontecristian.workflow.core.exception.definition.WorkflowDefinitionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionRuntimeReader;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import io.github.leontecristian.workflow.testhost.configuration.integration.CompletionAdapterIntegrationTest;
import io.github.leontecristian.workflow.testhost.dto.runtime.SandboxStart;
import io.github.leontecristian.workflow.testhost.repository.event.ConsoleEventStore;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;
import io.github.leontecristian.workflow.testhost.configuration.integration.RuntimeCommandFixture;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeTransaction;
import jakarta.persistence.EntityManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Committed family starts through the real sandbox transaction, link, action and durable outbox. */
@TestPropertySource(properties = {
        "workflow.console.enabled=true", "workflow.console.capabilities=sample",
        "workflow.console.admin.username=family.admin", "workflow.console.admin.password=integration-only-admin",
        "workflow.console.reviewer.username=family.reviewer", "workflow.console.reviewer.password=integration-only-reviewer"
})
@Import(RuntimeCommandFixture.class)
class FamilyWorkflowStartIT extends CompletionAdapterIntegrationTest {
    @Autowired WorkflowDefinitionService definitions;
    @Autowired WorkflowRuntimeQueryService reads;
    @Autowired TaskTypeCatalogService catalog;
    @Autowired ConsoleRuntimeTransaction runtime;
    @Autowired RuntimeCommandFixture runtimeCommands;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean SandboxRequestStore requests;
    @MockitoSpyBean ConsoleEventStore events;
    @MockitoSpyBean WorkflowDefinitionRuntimeReader reader;
    private final String marker = "family.it." + UUID.randomUUID();
    private final ObjectMapper json = new ObjectMapper();
    private Long familyId, ownedTaskType;

    private <T> T tx(Supplier<T> work) {
        return tx(work, false);
    }
    private <T> T tx(Supplier<T> work, boolean rollback) {
        var template = new TransactionTemplate(transactions);
        template.setTimeout(30);
        return template.execute(status -> {
            T result = work.get();
            if (rollback) status.setRollbackOnly();
            return result;
        });
    }
    private DefinitionManagementContext managementContext() {
        return new DefinitionManagementContext(marker, Instant.now());
    }
    private WorkflowCommandContext context() {
        return new WorkflowCommandContext(ActorKind.USER, marker, Instant.now(), marker);
    }
    @BeforeEach void prepareCatalog() {
        if (catalog.findByCode("SANDBOX_REVIEW").isEmpty())
            ownedTaskType = tx(() -> catalog.create(new CreateTaskType("SANDBOX_REVIEW", marker, null, true))).after().id();
    }
    @AfterEach void cleanOwnedFixtures() {
        reset(target(requests), target(events), target(reader));
        tx(() -> {
            var ids = jdbc.queryForList("SELECT id FROM wf.workflow_instance WHERE started_by=?", Long.class, marker);
            jdbc.update("DELETE FROM wf_test.sandbox_request WHERE created_by=? AND name=?", marker, marker);
            for (long id : ids) {
                jdbc.update("DELETE FROM wf_test.workflow_event_outbox WHERE instance_id=?", id);
                jdbc.update("DELETE FROM wf.workflow_command_receipt WHERE instance_id=?", id);
                jdbc.update("DELETE FROM wf.workflow_history WHERE instance_id=?", id);
                jdbc.update("DELETE FROM wf.workflow_variable WHERE instance_id=?", id);
                jdbc.update("DELETE FROM wf.workflow_task WHERE activity_id IN (SELECT id FROM wf.workflow_activity WHERE instance_id=?)", id);
                jdbc.update("DELETE FROM wf.workflow_activity WHERE instance_id=?", id);
                jdbc.update("DELETE FROM wf.workflow_instance WHERE id=?", id);
            }
            if (familyId != null) {
                jdbc.update("DELETE FROM wf.workflow_definition_version WHERE definition_id=?", familyId);
                jdbc.update("DELETE FROM wf.workflow_definition WHERE id=? AND workflow_key=?", familyId, marker);
            }
            if (ownedTaskType != null) jdbc.update("DELETE FROM wf.workflow_task_type WHERE id=?", ownedTaskType);
            return null;
        });
    }
    private static <T> T target(T bean) { return AopTestUtils.getUltimateTargetObject(bean); }
    private ObjectNode fixture(int version) throws Exception {
        var path = Path.of("docs/features/F006-test-host-definition-console/request-status-review.json");
        if (!Files.exists(path)) path = Path.of("..").resolve(path);
        var document = (ObjectNode) json.readTree(Files.readString(path));
        document.put("key", marker).put("version", version);
        return document;
    }
    private DefinitionVersionSummary draft(int version) throws Exception { return draft(fixture(version)); }
    private DefinitionVersionSummary draft(ObjectNode source) {
        var result = tx(() -> definitions.importDraft(new ImportWorkflowDraft(source.toString()), managementContext())).after();
        familyId = result.definitionId();
        return result;
    }
    private DefinitionVersionSummary publish(DefinitionVersionSummary draft) {
        tx(() -> {
            var result = definitions.publish(new CheckWorkflowDefinition(draft.id(), draft.lockVersion()), managementContext());
            assertThat(result.published()).withFailMessage(() -> result.report().toString()).isTrue();
            return result;
        });
        return summary(draft.id());
    }
    private DefinitionVersionSummary summary(long id) { return definitions.findVersion(id).orElseThrow().summary(); }
    private void retire(long id) {
        var version = summary(id);
        tx(() -> definitions.retire(new RetireWorkflowDefinition(id, version.lockVersion()), managementContext()));
    }
    private long request() { return tx(() -> requests.create(marker, marker, Instant.now())).id(); }
    private SandboxStart key(long request) {
        return new SandboxStart(UUID.randomUUID(), marker, null, null, request, 0, StartInputs.empty());
    }
    private SandboxStart family(long request) {
        return new SandboxStart(UUID.randomUUID(), null, familyId, null, request, 0, StartInputs.empty());
    }
    private WorkflowCommandResult start(SandboxStart command) { return runtime.start(command, context()); }
    private void pin(WorkflowCommandResult result, long version) {
        assertThat(result.execution().instance().definitionVersionId()).isEqualTo(version);
        long id = result.execution().instance().id();
        assertThat(jdbc.queryForObject("SELECT definition_version_id FROM wf.workflow_instance WHERE id=?", Long.class, id)).isEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_command_receipt WHERE instance_id=?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_history WHERE instance_id=? AND event_type='SERVICE_TASK_COMPLETED'", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_test.workflow_event_outbox WHERE instance_id=?", Integer.class, id)).isPositive();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_test.sandbox_request WHERE instance_id=? AND status='IN_REVIEW' AND lock_version=2", Integer.class, id)).isEqualTo(1);
    }
    private void noStart(SandboxStart command) {
        assertThat(requests.find(command.requestId()).instanceId()).isNull();
        assertThat(requests.find(command.requestId()).lockVersion()).isZero();
        assertThat(requests.find(command.requestId()).status()).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_instance WHERE business_type='SANDBOX_REQUEST' AND business_id=?", Integer.class, Long.toString(command.requestId()))).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_command_receipt WHERE command_id=?", Integer.class, command.commandId().toString())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_history WHERE command_id=?", Integer.class, command.commandId().toString())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_test.workflow_event_outbox WHERE command_id=?", Integer.class, command.commandId().toString())).isZero();
    }
    private void fails(SandboxStart command, String category) {
        assertThatThrownBy(() -> start(command)).isInstanceOfSatisfying(WorkflowExecutionException.class,
                error -> assertThat(error.category().name()).isEqualTo(category));
        noStart(command);
    }

    @Test void numericPublishedSelectionIgnoresRowIdentityTimeDraftRetiredAndCounter() throws Exception {
        var two = publish(draft(2));
        var seven = publish(draft(7));
        var nine = draft(9);
        var ten = publish(draft(10)); retire(ten.id());
        // Reinsert only this unreferenced fixture row with a fresh generated ID. Preserve its exact
        // immutable content/evidence; production allocation remains untouched. ID now disagrees with version.
        long reorderedTwo = tx(() -> {
            var row = jdbc.queryForMap("SELECT * FROM wf.workflow_definition_version WHERE id=?", two.id());
            row.remove("id");
            jdbc.update("DELETE FROM wf.workflow_definition_version WHERE id=?", two.id());
            String columns = String.join(",", row.keySet());
            String placeholders = String.join(",", java.util.Collections.nCopies(row.size(), "?"));
            return jdbc.queryForObject("INSERT INTO wf.workflow_definition_version(" + columns
                    + ") OUTPUT inserted.id VALUES(" + placeholders + ")", Long.class, row.values().toArray());
        });
        assertThat(reorderedTwo).isGreaterThan(seven.id());
        jdbc.update("UPDATE wf.workflow_definition_version SET published_at='2026-11-14T23:00:00' WHERE id=?", reorderedTwo);
        pin(start(key(request())), seven.id());
        pin(start(family(request())), seven.id());
        pin(start(new SandboxStart(UUID.randomUUID(), reorderedTwo, request(), 0)), reorderedTwo);
        assertThat(summary(reorderedTwo).status()).isEqualTo(DefinitionStatus.PUBLISHED);
        assertThat(summary(nine.id()).status()).isEqualTo(DefinitionStatus.DRAFT);
        assertThat(jdbc.queryForObject("SELECT highest_allocated_version FROM wf.workflow_definition WHERE id=?", Integer.class, familyId)).isEqualTo(10);
    }

    @Test void distinctMissingAndLifecycleErrorsCommitNothingAndLookupMissDoesNotResolve() throws Exception {
        var draft = draft(1);
        fails(key(request()), "NO_PUBLISHED_VERSION");
        fails(family(request()), "NO_PUBLISHED_VERSION");
        fails(new SandboxStart(UUID.randomUUID(), draft.id(), request(), 0), "INVALID_STATE");
        fails(new SandboxStart(UUID.randomUUID(), marker + ".unknown", null, null, request(), 0, StartInputs.empty()), "NOT_FOUND");
        fails(new SandboxStart(UUID.randomUUID(), null, Long.MAX_VALUE, null, request(), 0, StartInputs.empty()), "NOT_FOUND");
        fails(new SandboxStart(UUID.randomUUID(), Long.MAX_VALUE, request(), 0), "NOT_FOUND");
        var miss = key(request());
        doAnswer(call -> { throw new AssertionError("Lookup-only replay resolved a definition"); })
                .when(target(reader)).loadForStart(any(io.github.leontecristian.workflow.core.dto.runtime.StartWorkflow.class));
        assertThat(runtime.replayStart(miss, context())).isEmpty();
        noStart(miss);
    }

    @Test void latestInvalidInputsOrCapabilitiesFailWithoutOlderFallback() throws Exception {
        var older = publish(draft(1));
        var source = fixture(2);
        var declaration = (ObjectNode) source.path("startPolicy").path("inputContract").get(0);
        declaration.put("required", true); declaration.remove("default");
        var latest = publish(draft(source));
        fails(key(request()), "INVALID_INPUT");
        var attempted = new ArrayList<Long>();
        doAnswer(call -> {
            var selected = (io.github.leontecristian.workflow.core.dto.definition.document.ExecutableDefinition) call.getArgument(0);
            attempted.add(selected.versionId());
            throw new WorkflowDefinitionException(WorkflowDefinitionException.Category.HOST_CONFIGURATION, List.of());
        }).when(target(reader)).requireCapabilities(any(), org.mockito.ArgumentMatchers.eq(true));
        fails(family(request()), "HOST_CONFIGURATION");
        assertThat(attempted).containsExactly(latest.id());
        assertThat(summary(older.id()).status()).isEqualTo(DefinitionStatus.PUBLISHED);
    }

    @Test void replayRetainsOriginalPinAfterPublicationAndRetirementAndPinnedWorkContinues() throws Exception {
        var original = publish(draft(1));
        var command = key(request());
        var first = start(command); pin(first, original.id());
        var newer = publish(draft(2)); retire(original.id());
        pin(start(family(request())), newer.id());
        doAnswer(call -> { throw new AssertionError("Replay attempted fresh resolution"); })
                .when(target(reader)).loadForStart(any(io.github.leontecristian.workflow.core.dto.runtime.StartWorkflow.class));
        assertThat(start(command)).isEqualTo(new WorkflowCommandResult(true, first.execution()));
        assertThat(runtime.replayStart(command, context()).orElseThrow().execution()).isEqualTo(first.execution());
        var changed = new SandboxStart(command.commandId(), null, familyId, null, command.requestId(), 0, command.inputs());
        assertThatThrownBy(() -> start(changed)).isInstanceOfSatisfying(WorkflowExecutionException.class,
                error -> assertThat(error.category().name()).isEqualTo("COMMAND_ID_CONFLICT"));
        pin(first, original.id());
        reset(target(reader));
        var task = first.execution().task();
        var reviewer = new WorkflowCommandContext(ActorKind.USER, "family.reviewer", Instant.now(), marker);
        runtimeCommands.complete(new CompleteWorkflowTask(UUID.randomUUID(), first.execution().instance().id(), task.id(),
                first.execution().instance().lockVersion(), "APPROVE", null), reviewer);
        assertThat(reads.findInstance(first.execution().instance().id()).orElseThrow().definitionVersionId()).isEqualTo(original.id());
    }

    /** The first transaction owns the actual family lock; the second is observed pending before release. */
    private <T> T overlap(Supplier<?> winner, Supplier<T> contender) throws Exception {
        return overlap(winner, contender, false);
    }
    private <T> T overlap(Supplier<?> winner, Supplier<T> contender, boolean rollbackWinner) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var locked = new CountDownLatch(1);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> tx(() -> {
                jdbc.queryForObject("SELECT id FROM wf.workflow_definition WITH (UPDLOCK,ROWLOCK) WHERE id=?", Long.class, familyId);
                locked.countDown(); await(release);
                return winner.get();
            }, rollbackWinner));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            Future<T> second = pool.submit(() -> { entered.countDown(); return contender.get(); });
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            first.get(25, TimeUnit.SECONDS);
            return second.get(25, TimeUnit.SECONDS);
        } finally {
            release.countDown(); pool.shutdown();
            assertThat(pool.awaitTermination(35, TimeUnit.SECONDS)).isTrue();
        }
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Family start barrier timed out"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
    }

    @ParameterizedTest @ValueSource(strings = {"publish", "retire"})
    void lifecycleWinningSelectsCommittedVersion(String operation) throws Exception {
        var older = publish(draft(1));
        var next = draft(2);
        if (operation.equals("retire")) next = publish(next);
        final var candidate = next;
        var command = key(request());
        var selected = overlap(() -> {
            if (operation.equals("publish")) publish(candidate); else retire(candidate.id());
            return null;
        }, () -> start(command));
        pin(selected, operation.equals("publish") ? candidate.id() : older.id());
    }

    @ParameterizedTest @ValueSource(strings = {"publish", "retire"})
    void startWinningKeepsItsPinBeforeLifecycleProceeds(String operation) throws Exception {
        var older = publish(draft(1));
        var next = draft(2);
        if (operation.equals("retire")) next = publish(next);
        final var candidate = next;
        var command = key(request());
        var selected = new java.util.concurrent.atomic.AtomicReference<WorkflowCommandResult>();
        overlap(() -> { selected.set(start(command)); return null; }, () -> {
            if (operation.equals("publish")) publish(candidate); else retire(candidate.id());
            return null;
        });
        pin(selected.get(), operation.equals("publish") ? older.id() : candidate.id());
        pin(start(key(request())), operation.equals("publish") ? candidate.id() : older.id());
    }

    @ParameterizedTest @ValueSource(strings = {"publish", "retire"})
    void rolledBackLifecycleDoesNotChangeSelection(String operation) throws Exception {
        var older = publish(draft(1));
        var candidate = draft(2);
        if (operation.equals("retire")) candidate = publish(candidate);
        final var next = candidate;
        var command = key(request());
        var selected = overlap(() -> {
            if (operation.equals("publish")) publish(next); else retire(next.id());
            return null;
        }, () -> start(command), true);
        pin(selected, operation.equals("publish") ? older.id() : next.id());
        assertThat(summary(next.id()).status()).isEqualTo(operation.equals("publish") ? DefinitionStatus.DRAFT : DefinitionStatus.PUBLISHED);
    }

    @Test void familyLockTimeoutIsSanitizedAndCommitsNoPartialStart() throws Exception {
        publish(draft(1));
        var command = key(request());
        var entered = new CountDownLatch(1);
        var pool = Executors.newSingleThreadExecutor();
        try {
            tx(() -> {
                jdbc.queryForObject("SELECT id FROM wf.workflow_definition WITH (UPDLOCK,ROWLOCK) WHERE id=?", Long.class, familyId);
                var contender = pool.submit(() -> {
                    try {
                        return tx(() -> {
                            jdbc.execute("SET LOCK_TIMEOUT 1000");
                            entered.countDown();
                            try { start(command); return null; }
                            finally { jdbc.execute("SET LOCK_TIMEOUT -1"); }
                        });
                    } catch (WorkflowExecutionException error) { return error.category().name(); }
                });
                await(entered);
                try { assertThat(contender.get(10, TimeUnit.SECONDS)).isEqualTo("CONCURRENT_COMMAND"); }
                catch (Exception error) { throw new IllegalStateException("Lock-timeout contender failed", error); }
                return null;
            });
            noStart(command);
        } finally {
            pool.shutdown(); assertThat(pool.awaitTermination(35, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest @ValueSource(strings = {"publish", "retire"})
    void staleManagedFamilyAndCandidateAreRefreshedAfterLifecycleChange(String operation) throws Exception {
        var older = publish(draft(1));
        var candidate = draft(2);
        if (operation.equals("retire")) candidate = publish(candidate);
        final var newest = candidate;
        var command = key(request());
        var loaded = new CountDownLatch(1);
        var changed = new CountDownLatch(1);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var future = pool.submit(() -> tx(() -> {
                var stale = em.find(WorkflowDefinitionVersionEntity.class, newest.id());
                em.find(WorkflowDefinitionEntity.class, familyId);
                var initialStatus = operation.equals("publish") ? DefinitionStatus.DRAFT : DefinitionStatus.PUBLISHED;
                assertThat(stale.getStatus()).isEqualTo(initialStatus);
                loaded.countDown(); await(changed);
                assertThat(stale.getStatus()).isEqualTo(initialStatus);
                return start(command);
            }));
            assertThat(loaded.await(10, TimeUnit.SECONDS)).isTrue();
            if (operation.equals("publish")) publish(newest); else retire(newest.id());
            changed.countDown();
            pin(future.get(25, TimeUnit.SECONDS), operation.equals("publish") ? newest.id() : older.id());
        } finally {
            changed.countDown(); pool.shutdown();
            assertThat(pool.awaitTermination(35, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test void overlappingDuplicateRollsBackLoserAndLookupRecoveryReturnsOneCommittedEffectSet() throws Exception {
        var version = publish(draft(1));
        var command = key(request());
        var firstResult = new java.util.concurrent.atomic.AtomicReference<WorkflowCommandResult>();
        // Both receipt misses must occur before either command can obtain the family lock.
        var missed = new CountDownLatch(2);
        doAnswer(call -> { missed.countDown(); await(missed); return call.callRealMethod(); })
                .when(target(reader)).loadForStart(any(io.github.leontecristian.workflow.core.dto.runtime.StartWorkflow.class));
        var pool = Executors.newFixedThreadPool(2);
        try {
            Supplier<Object> attempt = () -> {
                try { var result = start(command); firstResult.compareAndSet(null, result); return result; }
                catch (RuntimeException failure) { return failure; }
            };
            var first = pool.submit(attempt::get); var second = pool.submit(attempt::get);
            var results = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertThat(results.stream().filter(WorkflowCommandResult.class::isInstance)).hasSize(1);
            assertThat(results.stream().filter(RuntimeException.class::isInstance)).hasSize(1);
            pin(firstResult.get(), version.id());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_instance WHERE started_by=?", Integer.class, marker)).isEqualTo(1);
            assertThat(runtime.replayStart(command, context()).orElseThrow().execution()).isEqualTo(firstResult.get().execution());
        } finally {
            pool.shutdown(); assertThat(pool.awaitTermination(35, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest @ValueSource(strings = {"link", "recorder"})
    void injectedFailureRollsBackLinkActionHistoryReceiptAndHandoffThenExplicitRetryResolvesAgain(String boundary) throws Exception {
        publish(draft(1));
        var command = key(request());
        if (boundary.equals("link")) {
            doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("F024 post-link failure"); })
                    .when(target(requests)).link(anyLong(), anyLong(), anyLong());
        } else {
            doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("F024 durable-recorder failure"); })
                    .when(target(events)).record(any(), anyString());
        }
        assertThatThrownBy(() -> start(command)).isInstanceOf(RuntimeException.class);
        noStart(command);
        reset(target(requests), target(events));
        var newer = publish(draft(2));
        pin(start(command), newer.id());
    }
}
