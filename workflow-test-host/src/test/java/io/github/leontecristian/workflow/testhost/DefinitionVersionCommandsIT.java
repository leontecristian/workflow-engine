package io.github.leontecristian.workflow.testhost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.leontecristian.workflow.core.dto.definition.CheckWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.CreateWorkflowDraftFromVersion;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionChange;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionManagementContext;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionStatus;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionVersionSummary;
import io.github.leontecristian.workflow.core.dto.definition.DeleteWorkflowDraft;
import io.github.leontecristian.workflow.core.dto.definition.ImportWorkflowDraft;
import io.github.leontecristian.workflow.core.dto.definition.ReplaceWorkflowDraft;
import io.github.leontecristian.workflow.core.exception.definition.WorkflowDefinitionException;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.testhost.repository.audit.ManagementAuditStore;
import io.github.leontecristian.workflow.testhost.configuration.integration.DatabaseIntegrationTest;
import io.github.leontecristian.workflow.web.service.administration.WorkflowAdministrationService;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import io.github.leontecristian.workflow.core.dto.definition.RetireWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.tasktype.CreateTaskType;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import org.springframework.test.util.AopTestUtils;

/** Committed SQL Server observations; fixture cleanup is restricted to this test's family/actor. */
@SpringBootTest(classes = WorkflowTestHostApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"workflow.console.enabled=true", "workflow.console.admin.username=f023-admin",
                "workflow.console.admin.password=f023-local-test", "workflow.console.reviewer.username=f023-reviewer",
                "workflow.console.reviewer.password=f023-local-test"})
class DefinitionVersionCommandsIT extends DatabaseIntegrationTest {
    @Autowired WorkflowDefinitionService definitions;
    @Autowired WorkflowAdministrationService management;
    @MockitoSpyBean ManagementAuditStore audits;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @Autowired TaskTypeCatalogService catalog;
    private Long ownedTaskType;
    private final String marker = "f023." + UUID.randomUUID();
    private final DefinitionManagementContext context = new DefinitionManagementContext(marker, Instant.parse("2026-11-14T10:00:00Z"));

    private <T> T tx(Supplier<T> work) { return new TransactionTemplate(transactions).execute(status -> work.get()); }
    private String document(int version) {
        return "{\"schemaVersion\":1,\"key\":\"" + marker + "\",\"version\":" + version
                + ",\"name\":\"F023\",\"designer\":{\"version\":19,\"number\":900719925474099312345,\"decimal\":1.2300}}";
    }
    private DefinitionVersionSummary imported(int version) {
        return tx(() -> definitions.importDraft(new ImportWorkflowDraft(document(version)), context)).after();
    }
    private DefinitionChange copy(DefinitionVersionSummary source) {
        return tx(() -> definitions.createDraftFromVersion(new CreateWorkflowDraftFromVersion(source.id(), source.lockVersion()), context));
    }
    private Object delete(DefinitionVersionSummary source) {
        return tx(() -> definitions.deleteDraft(new DeleteWorkflowDraft(source.id(), source.lockVersion()), context));
    }
    private int allocated(long family) {
        return jdbc.queryForObject("SELECT highest_allocated_version FROM wf.workflow_definition WHERE id=?", Integer.class, family);
    }
    private void conflict(Runnable work) {
        assertThatThrownBy(work::run).isInstanceOfSatisfying(WorkflowDefinitionException.class,
                e -> assertThat(e.category()).isIn(WorkflowDefinitionException.Category.CONFLICT, WorkflowDefinitionException.Category.INVALID_STATE));
    }

    @AfterEach void cleanup() {
        reset(AopTestUtils.<ManagementAuditStore>getUltimateTargetObject(audits));
        tx(() -> {
            jdbc.update("DELETE FROM wf_test.management_audit WHERE actor_ref=?", marker);
            jdbc.update("DELETE FROM wf.workflow_instance WHERE started_by=?", marker);
            jdbc.update("DELETE FROM wf.workflow_definition_version WHERE definition_id IN (SELECT id FROM wf.workflow_definition WHERE workflow_key=?)", marker);
            jdbc.update("DELETE FROM wf.workflow_definition WHERE workflow_key=?", marker);
            if (ownedTaskType != null) jdbc.update("DELETE FROM wf.workflow_task_type WHERE id=?", ownedTaskType);
            return null;
        });
    }

    @Test void counterSurvivesDeletionAndFreshTransactionsAndUnusedGapsAreRejected() {
        var source = imported(2); imported(8);
        String original = definitions.exportVersion(source.id());
        var highest = copy(source).after();
        assertThat(highest.version()).isEqualTo(9);
        assertThat(definitions.exportVersion(highest.id())).contains("900719925474099312345", "1.2300", "\"version\":19");
        assertThat(highest.contentRevision()).isEqualTo(1);
        assertThat(highest.validation()).isNull();
        assertThat(definitions.exportVersion(source.id())).isEqualTo(original);
        delete(highest);
        assertThat(definitions.findVersion(highest.id())).isEmpty();
        assertThatThrownBy(() -> definitions.exportVersion(highest.id())).isInstanceOf(WorkflowDefinitionException.class);
        conflict(() -> imported(9));
        conflict(() -> imported(4));
        assertThat(tx(() -> definitions.importDraft(new ImportWorkflowDraft(document(2)), context)).changed()).isFalse();
        assertThat(copy(source).after().version()).isEqualTo(10);
        assertThat(allocated(source.definitionId())).isEqualTo(10);
        assertThatThrownBy(() -> delete(highest)).isInstanceOfSatisfying(WorkflowDefinitionException.class,
                e -> assertThat(e.category()).isEqualTo(WorkflowDefinitionException.Category.NOT_FOUND));
    }

    @Test void deletingAllDraftsKeepsFamilyAndExhaustionReservation() {
        var source = imported(1);
        var maximum = imported(Integer.MAX_VALUE);
        delete(maximum);
        conflict(() -> copy(source));
        assertThat(allocated(source.definitionId())).isEqualTo(Integer.MAX_VALUE);
        delete(source);
        assertThat(definitions.findDefinition(source.definitionId())).isPresent();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_definition_version WHERE definition_id=?", Integer.class, source.definitionId())).isZero();
        conflict(() -> imported(Integer.MAX_VALUE));
    }

    @Test void rollbackRestoresDeletionAndRemovesCreationAndReservationWithActualHostAudit() {
        var source = imported(1);
        doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("F023 test audit failure"); })
                .when(AopTestUtils.<ManagementAuditStore>getUltimateTargetObject(audits)).insert(any(), any(), any(), any(), anyLong(), any(), any());
        assertThatThrownBy(() -> management.createDraftFromVersion(marker, source.id(), source.lockVersion(), UUID.randomUUID().toString()))
                .isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class).hasRootCauseMessage("F023 test audit failure");
        assertThat(allocated(source.definitionId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_definition_version WHERE definition_id=?", Integer.class, source.definitionId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_test.management_audit WHERE actor_ref=?", Integer.class, marker)).isZero();
        assertThatThrownBy(() -> management.deleteDraft(marker, source.id(), source.lockVersion(), UUID.randomUUID().toString()))
                .isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class).hasRootCauseMessage("F023 test audit failure");
        assertThat(definitions.findVersion(source.id())).isPresent();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_test.management_audit WHERE actor_ref=?", Integer.class, marker)).isZero();
        reset(AopTestUtils.<ManagementAuditStore>getUltimateTargetObject(audits));
        String correlation = UUID.randomUUID().toString();
        var result = management.deleteDraft(marker, source.id(), source.lockVersion(), correlation);
        assertThat(result.before().id()).isEqualTo(source.id());
        var audit = jdbc.queryForMap("SELECT * FROM wf_test.management_audit WHERE correlation_id=?", correlation);
        assertThat(audit.get("entity_id")).isEqualTo(source.id());
        assertThat((String) audit.get("evidence_json")).contains("\"after\":null", "DELETE_DRAFT", "\"before\":{").doesNotContain("designer", "definitionJson");
        assertThat(definitions.findVersion(source.id())).isEmpty();
        assertThat(allocated(source.definitionId())).isEqualTo(1);
    }

    @Test void outerRollbackAfterSuccessfulSharedServiceReturnRemovesMutationAndAudit() {
        var source = imported(1);
        String copyCorrelation = UUID.randomUUID().toString();
        String deleteCorrelation = UUID.randomUUID().toString();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var copy = management.createDraftFromVersion(marker, source.id(), source.lockVersion(), copyCorrelation);
            assertThat(copy.after().version()).isEqualTo(2);
            management.deleteDraft(marker, source.id(), source.lockVersion(), deleteCorrelation);
            assertThat(allocated(source.definitionId())).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_test.management_audit WHERE actor_ref=?",
                    Integer.class, marker)).isEqualTo(2);
            status.setRollbackOnly();
        });
        // The owning transaction has completed; these observations use fresh connections/transactions.
        assertThat(definitions.findVersion(source.id())).isPresent();
        assertThat(allocated(source.definitionId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_definition_version WHERE definition_id=?",
                Integer.class, source.definitionId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_test.management_audit WHERE actor_ref=?",
                Integer.class, marker)).isZero();
    }

    @Test void joinedAuditFailureMarksOuterTransactionRollbackOnly() {
        var source = imported(1);
        doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("F029 joined audit failure"); })
                .when(AopTestUtils.<ManagementAuditStore>getUltimateTargetObject(audits))
                .insert(any(), any(), any(), any(), anyLong(), any(), any());
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertThatThrownBy(() -> management.createDraftFromVersion(marker, source.id(), source.lockVersion(),
                    UUID.randomUUID().toString()))
                    .isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
                    .hasRootCauseMessage("F029 joined audit failure");
            assertThat(status.isRollbackOnly()).as("The managed REQUIRED service joins the host transaction").isTrue();
        })).isInstanceOf(UnexpectedRollbackException.class);
        assertThat(allocated(source.definitionId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_definition_version WHERE definition_id=?",
                Integer.class, source.definitionId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_test.management_audit WHERE actor_ref=?",
                Integer.class, marker)).isZero();
    }

    /** Hold the actual family lock in the winning transaction before starting the losing command. */
    private <T> T overlap(DefinitionVersionSummary source, Supplier<?> winner, Supplier<T> contender) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var locked = new CountDownLatch(1);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        Future<?> first = null;
        Future<T> second = null;
        try {
            first = pool.submit(() -> tx(() -> {
                jdbc.queryForObject("SELECT id FROM wf.workflow_definition WITH (UPDLOCK, ROWLOCK) WHERE id=?", Long.class, source.definitionId());
                locked.countDown();
                await(release);
                return winner.get();
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            second = pool.submit(() -> { started.countDown(); return contender.get(); });
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
            // The contender has entered while the family claim is held; it cannot commit yet.
            Future<T> pending = second;
            assertThatThrownBy(() -> secondResult(pending)).isInstanceOf(java.util.concurrent.TimeoutException.class);
            release.countDown();
            first.get(20, TimeUnit.SECONDS);
            return second.get(20, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }
    private static Object secondResult(Future<?> future) throws Exception { return future.get(200, TimeUnit.MILLISECONDS); }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Barrier timed out"); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
    }

    @Test void overlappingCreatesAndImportShareAllocationLock() throws Exception {
        var source = imported(1);
        var next = overlap(source, () -> definitions.createDraftFromVersion(new CreateWorkflowDraftFromVersion(source.id(), source.lockVersion()), context), () -> copy(source));
        assertThat(next.after().version()).isEqualTo(3);
        var afterImport = overlap(source, () -> definitions.importDraft(new ImportWorkflowDraft(document(7)), context), () -> copy(source));
        assertThat(afterImport.after().version()).isEqualTo(8);
        assertThat(allocated(source.definitionId())).isEqualTo(8);
    }

    @Test void overlappingSaveMakesCopyStaleAndDeleteWaitsForCopy() throws Exception {
        var source = imported(1);
        var failure = overlap(source,
                () -> definitions.replaceDraft(new ReplaceWorkflowDraft(source.id(), source.lockVersion(), document(1).replace("F023", "Changed")), context),
                () -> { try { copy(source); return null; } catch (WorkflowDefinitionException e) { return e.category(); } });
        assertThat(failure).isEqualTo(WorkflowDefinitionException.Category.CONFLICT);
        var current = definitions.findVersion(source.id()).orElseThrow().summary();
        var removed = overlap(current,
                () -> definitions.createDraftFromVersion(new CreateWorkflowDraftFromVersion(current.id(), current.lockVersion()), context),
                () -> delete(current));
        assertThat(removed).isNotNull();
        assertThat(definitions.findVersion(source.id())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_definition_version WHERE definition_id=?", Integer.class, source.definitionId())).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"save", "validate", "publish", "copy", "delete"})
    void deletionWinningRejectsOverlappingSourceOperations(String operation) throws Exception {
        var source = imported(1);
        var result = overlap(source, () -> definitions.deleteDraft(new DeleteWorkflowDraft(source.id(), source.lockVersion()), context),
                () -> {
                    try {
                        switch (operation) {
                            case "save" -> tx(() -> definitions.replaceDraft(new ReplaceWorkflowDraft(source.id(), source.lockVersion(), document(1)), context));
                            case "validate" -> tx(() -> definitions.validateDraft(new CheckWorkflowDefinition(source.id(), source.lockVersion()), context));
                            case "publish" -> tx(() -> definitions.publish(new CheckWorkflowDefinition(source.id(), source.lockVersion()), context));
                            case "delete" -> delete(source);
                            default -> copy(source);
                        }
                        return null;
                    } catch (WorkflowDefinitionException e) { return e.category(); }
                });
        assertThat(result).isEqualTo(WorkflowDefinitionException.Category.NOT_FOUND);
        assertThatThrownBy(() -> tx(() -> definitions.replaceDraft(new ReplaceWorkflowDraft(source.id(), source.lockVersion(), document(1)), context)))
                .isInstanceOf(WorkflowDefinitionException.class);
        assertThatThrownBy(() -> tx(() -> definitions.validateDraft(new CheckWorkflowDefinition(source.id(), source.lockVersion()), context)))
                .isInstanceOf(WorkflowDefinitionException.class);
        assertThatThrownBy(() -> delete(source)).isInstanceOf(WorkflowDefinitionException.class);
    }

    @Test void publicationWinningRejectsDeletionAndImmutableSourcesRemainCopyable() throws Exception {
        var task = catalog.findByCode("SANDBOX_REVIEW").orElseGet(() -> {
            var created = tx(() -> catalog.create(new CreateTaskType("SANDBOX_REVIEW", marker, null, true))).after();
            ownedTaskType = created.id();
            return created;
        });
        assertThat(task.selectable()).isTrue();
        String valid = """
                {"schemaVersion":1,"key":"%s","version":1,"name":"Lifecycle fixture","startPolicy":{"inputContract":[]},"variables":[],
                 "nodes":[{"key":"begin","type":"START","name":"Begin"},
                  {"key":"review","type":"HUMAN_TASK","name":"Review","taskType":"SANDBOX_REVIEW",
                   "assignment":{"resolver":"sandbox.singleReviewer","inputs":{}},"completion":{"policy":"SINGLE"},
                   "resultContract":{"mode":"FORBIDDEN"},"outcomes":[{"key":"COMPLETE","label":"Complete","requiresComment":false}]},
                  {"key":"end","type":"END","name":"End","result":"DONE"}],
                 "transitions":[{"key":"first","from":"begin","to":"review"},{"key":"last","from":"review","to":"end","outcome":"COMPLETE"}]}
                """.formatted(marker);
        var source = tx(() -> definitions.importDraft(new ImportWorkflowDraft(valid), context)).after();
        var loser = overlap(source, () -> {
            var published = definitions.publish(new CheckWorkflowDefinition(source.id(), source.lockVersion()), context);
            assertThat(published.published()).withFailMessage(() -> published.report().toString()).isTrue();
            return published;
        }, () -> { try { delete(source); return null; } catch (WorkflowDefinitionException e) { return e.category(); } });
        assertThat(loser).isEqualTo(WorkflowDefinitionException.Category.CONFLICT);
        var published = definitions.findVersion(source.id()).orElseThrow().summary();
        assertThat(copy(published).after().status()).isEqualTo(DefinitionStatus.DRAFT);
        conflict(() -> delete(published));
        var retired = tx(() -> definitions.retire(new RetireWorkflowDefinition(source.id(), published.lockVersion()), context)).after();
        assertThat(copy(retired).after().status()).isEqualTo(DefinitionStatus.DRAFT);
        conflict(() -> delete(retired));
        assertThat(definitions.findVersion(source.id()).orElseThrow().summary().status()).isEqualTo(DefinitionStatus.RETIRED);
    }

    @Test void unexpectedReferenceRejectsDeletionWithoutRemovingDraftOrCounter() {
        var source = imported(1);
        tx(() -> {
            jdbc.update("INSERT INTO wf.workflow_instance(definition_version_id,current_node_key,status,started_at,started_by,last_history_sequence,lock_version) VALUES (?,'fixture','ACTIVE','2026-11-14',?,0,0)", source.id(), marker);
            return null;
        });
        conflict(() -> delete(source));
        assertThat(definitions.findVersion(source.id())).isPresent();
        assertThat(allocated(source.definitionId())).isEqualTo(1);
    }
}
