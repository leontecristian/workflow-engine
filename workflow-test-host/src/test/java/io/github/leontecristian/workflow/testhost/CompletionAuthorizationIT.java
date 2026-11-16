package io.github.leontecristian.workflow.testhost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leontecristian.workflow.core.dto.definition.CheckWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionManagementContext;
import io.github.leontecristian.workflow.core.dto.definition.ImportWorkflowDraft;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowExecutionResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.dto.tasktype.CreateTaskType;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import io.github.leontecristian.workflow.testhost.configuration.integration.DatabaseIntegrationTest;
import io.github.leontecristian.workflow.testhost.dto.runtime.SandboxStart;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeTransaction;
import io.github.leontecristian.workflow.testhost.service.runtime.SandboxCompletionPermissions;
import io.github.leontecristian.workflow.testhost.service.runtime.SandboxTaskCompletionValidator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Real commits/rollback and one bounded business-row race, on the guarded SQL test target only. */
@SpringBootTest(classes = WorkflowTestHostApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"workflow.console.enabled=true", "workflow.console.capabilities=sample",
                "workflow.console.admin.username=f028-admin", "workflow.console.admin.password=f028-local-password",
                "workflow.console.reviewer.username=f028-reviewer", "workflow.console.reviewer.password=f028-local-password"})
class CompletionAuthorizationIT extends DatabaseIntegrationTest {
    @Autowired WorkflowDefinitionService definitions;
    @Autowired TaskTypeCatalogService catalog;
    @Autowired WorkflowRuntimeQueryService reads;
    @Autowired ConsoleRuntimeTransaction runtime;
    @Autowired SandboxRequestStore requests;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean SandboxTaskCompletionValidator validator;
    @MockitoSpyBean SandboxCompletionPermissions permissions;
    private final String marker = "f028.it." + UUID.randomUUID();
    private Long instanceId, requestId, familyId, versionId, ownedTaskType;
    private static final String VALID = "{\"completionFixture\":{\"approvalBasis\":{\"amount\":1},\"currentBasis\":{\"amount\":1}}}";
    private static final String CHANGED = "{\"completionFixture\":{\"approvalBasis\":{\"amount\":1},\"currentBasis\":{\"amount\":2}}}";

    private TransactionTemplate tx() { var tx = new TransactionTemplate(transactions); tx.setTimeout(20); return tx; }
    private WorkflowCommandContext actor(String name) { return new WorkflowCommandContext(ActorKind.USER, name, Instant.now(), marker); }
    private <T> T reviewer(Supplier<T> action) {
        var previous = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("f028-reviewer", "unused",
                List.of(new SimpleGrantedAuthority("ROLE_CONSOLE_REVIEWER"))));
        SecurityContextHolder.setContext(context);
        try { return action.get(); } finally { SecurityContextHolder.setContext(previous); }
    }
    private WorkflowExecutionResult start() throws Exception {
        var path = Path.of("docs/features/F021-all-collective-review/sandbox-all-review.json");
        if (!Files.exists(path)) path = Path.of("..").resolve(path);
        var source = (ObjectNode) new ObjectMapper().readTree(Files.readString(path));
        source.put("key", marker);
        if (catalog.findByCode("SANDBOX_REVIEW").isEmpty())
            ownedTaskType = tx().execute(t -> catalog.create(new CreateTaskType("SANDBOX_REVIEW", marker, null, true)).after().id());
        tx().executeWithoutResult(t -> {
            var context = new DefinitionManagementContext(marker, Instant.now());
            var draft = definitions.importDraft(new ImportWorkflowDraft(source.toString()), context).after();
            versionId = draft.id(); familyId = draft.definitionId();
            assertThat(definitions.publish(new CheckWorkflowDefinition(versionId, draft.lockVersion()), context).published()).isTrue();
        });
        requestId = tx().execute(t -> requests.create(marker, VALID, marker, Instant.now())).id();
        var state = runtime.start(new SandboxStart(UUID.randomUUID(), versionId, requestId, 0), actor("f028-admin")).execution();
        instanceId = state.instance().id();
        return state;
    }
    private List<WorkflowTaskView> tasks() { return reads.listTasks(instanceId, 0, 100).items(); }
    private CompleteWorkflowTask command(WorkflowTaskView task) {
        return new CompleteWorkflowTask(UUID.randomUUID(), instanceId, task.id(),
                reads.findInstance(instanceId).orElseThrow().lockVersion(), "APPROVE", null,
                CompleteWorkflowTask.parseResult("{\"note\":\"accepted\"}"));
    }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE instance_id=?", Integer.class, instanceId); }
    private void material(String value) { tx().executeWithoutResult(t -> jdbc.update("UPDATE wf_test.sandbox_request SET request_data_json=? WHERE id=?", value, requestId)); }

    @Test void contextualViewGrantCannotCompleteWithoutDecideEvenWhenAssigned() throws Exception {
        start();
        tx().executeWithoutResult(t -> jdbc.update("UPDATE wf_test.sandbox_request SET name=? WHERE id=?",
                "F028-SECONDARY:" + marker, requestId));
        var attempt = command(tasks().stream().filter(t -> "DIRECT".equals(t.taskKey())).findFirst().orElseThrow());
        long version = reads.findInstance(instanceId).orElseThrow().lockVersion();
        assertThatThrownBy(() -> reviewer(() -> runtime.complete(attempt, actor("f028-reviewer"))))
                .isInstanceOfSatisfying(WorkflowExecutionException.class, e -> assertThat(e.category()).isEqualTo(Category.COMPLETION_FORBIDDEN));
        assertThat(reads.findInstance(instanceId).orElseThrow().lockVersion()).isEqualTo(version);
        assertThat(reads.findTask(attempt.taskId()).orElseThrow().status()).isEqualTo(TaskStatus.OPEN);
        org.mockito.Mockito.verifyNoInteractions(validator);
    }

    @Test void rejectionRollsBackProvisionalVersionAndPreservesPriorAllContributionThenReplaySkipsFreshHooks() throws Exception {
        start();
        var first = command(tasks().get(0));
        var accepted = reviewer(() -> runtime.complete(first, actor("f028-reviewer")));
        assertThat(accepted.replayed()).isFalse();
        var remaining = tasks().stream().filter(t -> t.status() == TaskStatus.OPEN).findFirst().orElseThrow();
        var attempt = command(remaining);
        long version = reads.findInstance(instanceId).orElseThrow().lockVersion();
        int history = count("wf.workflow_history"), receipts = count("wf.workflow_command_receipt"), handoff = count("wf_test.workflow_event_outbox"), effects = count("wf_test.service_effect");
        material(CHANGED);
        assertThatThrownBy(() -> reviewer(() -> runtime.complete(attempt, actor("f028-reviewer"))))
                .isInstanceOfSatisfying(WorkflowExecutionException.class, e -> assertThat(e.category()).isEqualTo(Category.BUSINESS_VALIDATION_REJECTED));
        assertThat(reads.findInstance(instanceId).orElseThrow().lockVersion()).isEqualTo(version);
        assertThat(reads.findTask(remaining.id()).orElseThrow().status()).isEqualTo(TaskStatus.OPEN);
        assertThat(reads.findTaskSubmission(first.taskId())).isPresent();
        assertThat(reads.findTaskSubmission(remaining.id())).isEmpty();
        assertThat(count("wf.workflow_history")).isEqualTo(history);
        assertThat(count("wf.workflow_command_receipt")).isEqualTo(receipts);
        assertThat(count("wf_test.workflow_event_outbox")).isEqualTo(handoff);
        assertThat(count("wf_test.service_effect")).isEqualTo(effects);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_variable WHERE instance_id=? AND variable_key='reviewCollection'", Integer.class, instanceId)).isZero();

        reset(validator);
        doReturn(false).when(permissions).isAllowed(any(), any(), org.mockito.ArgumentMatchers.eq(SandboxCompletionPermissions.Scope.DECIDE), any());
        var replay = reviewer(() -> runtime.replayComplete(first, actor("f028-reviewer"))).orElseThrow();
        assertThat(replay.execution()).isEqualTo(accepted.execution());
        assertThat(reviewer(() -> runtime.complete(first, actor("f028-reviewer"))).replayed()).isTrue();
        org.mockito.Mockito.verifyNoInteractions(validator);
        doReturn(false).when(permissions).isAllowed(any(), any(), org.mockito.ArgumentMatchers.eq(SandboxCompletionPermissions.Scope.VIEW), any());
        assertThatThrownBy(() -> reviewer(() -> runtime.replayComplete(first, actor("f028-reviewer"))))
                .isInstanceOfSatisfying(WorkflowExecutionException.class, e -> assertThat(e.category()).isEqualTo(Category.NOT_FOUND));

        reset(permissions);
        material(VALID);
        for (var task : tasks().stream().filter(t -> t.status() == TaskStatus.OPEN).toList()) {
            reset(validator);
            var next = command(task);
            assertThat(reviewer(() -> runtime.complete(next, actor("f028-reviewer"))).replayed()).isFalse();
            verify(validator).validate(any(), any());
        }
        assertThat(tasks()).allMatch(t -> t.status() == TaskStatus.COMPLETED);
        assertThat(count("wf_test.service_effect")).isEqualTo(effects + 1);
    }

    @Test void protectedMaterialEditCannotCommitBetweenValidationAndContributionAcceptance() throws Exception {
        start();
        var command = command(tasks().get(0));
        var validated = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            validated.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(validator).validate(any(), any());
        var executor = Executors.newFixedThreadPool(2);
        try {
            var completion = executor.submit(() -> reviewer(() -> runtime.complete(command, actor("f028-reviewer"))));
            assertThat(validated.await(10, TimeUnit.SECONDS)).isTrue();
            var edit = executor.submit(() -> tx().executeWithoutResult(t -> {
                jdbc.execute("SET LOCK_TIMEOUT 750");
                try { jdbc.update("UPDATE wf_test.sandbox_request SET request_data_json=? WHERE id=?", CHANGED, requestId); }
                finally { jdbc.execute("SET LOCK_TIMEOUT -1"); }
            }));
            assertThatThrownBy(() -> edit.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(org.springframework.dao.DataAccessException.class);
            release.countDown();
            assertThat(completion.get(10, TimeUnit.SECONDS).replayed()).isFalse();
            assertThat(requests.find(requestId).requestDataJson()).isEqualTo(io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue.parse(VALID).toJson());
            assertThat(reads.findTask(command.taskId()).orElseThrow().status()).isEqualTo(TaskStatus.COMPLETED);
            verify(validator).validate(any(), any());
        } finally { release.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }

    @AfterEach void cleanupOwnedFixtures() {
        reset(validator, permissions);
        tx().executeWithoutResult(t -> {
            if (instanceId != null) {
                jdbc.update("DELETE FROM wf_test.workflow_event_inbox WHERE event_id IN (SELECT event_id FROM wf_test.workflow_event_outbox WHERE instance_id=?)", instanceId);
                for (var table : List.of("wf_test.workflow_event_outbox", "wf_test.service_effect", "wf.workflow_command_receipt", "wf.workflow_history", "wf.workflow_variable"))
                    jdbc.update("DELETE FROM " + table + " WHERE instance_id=?", instanceId);
                jdbc.update("DELETE FROM wf.workflow_task WHERE activity_id IN (SELECT id FROM wf.workflow_activity WHERE instance_id=?)", instanceId);
                jdbc.update("DELETE FROM wf.workflow_activity WHERE instance_id=?", instanceId);
                jdbc.update("DELETE FROM wf.workflow_instance WHERE id=?", instanceId);
            }
            if (requestId != null) jdbc.update("DELETE FROM wf_test.sandbox_request WHERE id=? AND created_by=?", requestId, marker);
            if (versionId != null) jdbc.update("DELETE FROM wf.workflow_definition_version WHERE id=?", versionId);
            if (familyId != null) jdbc.update("DELETE FROM wf.workflow_definition WHERE id=? AND workflow_key=?", familyId, marker);
            if (ownedTaskType != null) jdbc.update("DELETE FROM wf.workflow_task_type WHERE id=? AND code='SANDBOX_REVIEW'", ownedTaskType);
        });
    }
}
