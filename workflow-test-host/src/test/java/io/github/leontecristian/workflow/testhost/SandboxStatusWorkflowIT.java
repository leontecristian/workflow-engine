package io.github.leontecristian.workflow.testhost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leontecristian.workflow.core.dto.definition.CheckWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionManagementContext;
import io.github.leontecristian.workflow.core.dto.definition.ImportWorkflowDraft;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionContext;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.TextLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowExecutionResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
import io.github.leontecristian.workflow.core.dto.tasktype.CreateTaskType;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import io.github.leontecristian.workflow.testhost.configuration.integration.DatabaseIntegrationTest;
import io.github.leontecristian.workflow.testhost.dto.runtime.SandboxStart;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeTransaction;
import io.github.leontecristian.workflow.testhost.service.sandbox.SandboxSetRequestStatusAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Real sample adapters/commands on the guarded DB, with no HTTP listener or dev DB access. */
@TestPropertySource(properties = {
        "workflow.console.enabled=true", "workflow.console.capabilities=sample",
        "workflow.console.admin.username=status.admin", "workflow.console.admin.password=integration-only-admin",
        "workflow.console.reviewer.username=status.reviewer", "workflow.console.reviewer.password=integration-only-reviewer"
})
class SandboxStatusWorkflowIT extends DatabaseIntegrationTest {
    @Autowired private WorkflowDefinitionService definitions;
    @Autowired private WorkflowRuntimeQueryService reads;
    @Autowired private TaskTypeCatalogService catalog;
    @Autowired private SandboxRequestStore requests;
    @Autowired private ConsoleRuntimeTransaction runtime;
    @Autowired private SandboxSetRequestStatusAction action;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    private final ObjectMapper json = new ObjectMapper();
    private final String marker = "status.it." + UUID.randomUUID();
    private final List<Long> instances = new ArrayList<>();
    private Long versionId, familyId, ownedTaskType;

    private TransactionTemplate tx() { return new TransactionTemplate(transactions); }
    private WorkflowCommandContext context(boolean reviewer) {
        return new WorkflowCommandContext(ActorKind.USER, reviewer ? "status.reviewer" : "status.admin",
                Instant.now(), marker);
    }
    @BeforeEach void catalog() {
        if (catalog.findByCode("SANDBOX_REVIEW").isEmpty()) {
            ownedTaskType = tx().execute(t -> catalog.create(new CreateTaskType("SANDBOX_REVIEW", marker, null, true)).after().id());
        }
    }
    @AfterEach void cleanOwnedFixtures() {
        tx().executeWithoutResult(t -> {
            for (long instance : instances) {
                jdbc.update("DELETE FROM wf_test.workflow_event_outbox WHERE instance_id=?", instance);
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
    private ObjectNode fixture() throws Exception {
        Path source = Path.of("docs/features/F006-test-host-definition-console/request-status-review.json");
        if (!Files.exists(source)) source = Path.of("..").resolve(source);
        var root = (ObjectNode) json.readTree(Files.readString(source));
        root.put("key", marker);
        return root;
    }
    private long publish(ObjectNode source) {
        return tx().execute(t -> {
            var draft = definitions.importDraft(new ImportWorkflowDraft(source.toString()),
                    new DefinitionManagementContext(marker, Instant.now())).after();
            versionId = draft.id(); familyId = draft.definitionId();
            var checked = definitions.publish(new CheckWorkflowDefinition(draft.id(), draft.lockVersion()),
                    new DefinitionManagementContext(marker, Instant.now()));
            assertThat(checked.published()).withFailMessage(() -> checked.report().toString()).isTrue();
            return draft.id();
        });
    }
    private long request() {
        return tx().execute(t -> requests.create(marker, marker, Instant.now())).id();
    }
    private WorkflowExecutionResult start(SandboxStart command) {
        var result = runtime.start(command, context(false)).execution();
        instances.add(result.instance().id());
        return result;
    }
    private CompleteWorkflowTask complete(WorkflowExecutionResult start, String outcome) {
        return new CompleteWorkflowTask(UUID.randomUUID(), start.instance().id(), start.task().id(),
                start.instance().lockVersion(), outcome, outcome.equals("REJECT") ? "Sandbox rejection" : null);
    }
    private int count(String table, long instance) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE instance_id=?", Integer.class, instance);
    }
    private String variableJson(long instance) {
        var variable = reads.listVariables(instance, null, 100, null).items().stream()
                .filter(v -> v.key().equals("requestData")).findFirst().orElseThrow();
        return ((io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue) variable.value().value()).toJson();
    }

    @Test void absentSourceUsesPinnedDefaultAndArraySourceIsSupported() throws Exception {
        long definition = publish(fixture()), empty = request();
        assertThat(requests.find(empty).requestDataJson()).isNull();
        assertThat(variableJson(start(new SandboxStart(UUID.randomUUID(), definition, empty, 0)).instance().id()))
                .isEqualTo("{}");
        long array = tx().execute(t -> requests.create(marker, "[1.00,{\"flag\":true}]", marker, Instant.now())).id();
        assertThat(variableJson(start(new SandboxStart(UUID.randomUUID(), definition, array, 0)).instance().id()))
                .isEqualTo("[1.00,{\"flag\":true}]");
        assertThat(new io.github.leontecristian.workflow.testhost.service.console.ConsoleWire()
                .json(requests.page(array - 1, 1))).doesNotContain("requestDataJson");
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void requiredMissingAndUndeclaredSourceFailWithoutHostChanges(boolean required) throws Exception {
        var source = fixture();
        var policy = (ObjectNode) source.path("startPolicy");
        if (required) {
            var declaration = (ObjectNode) policy.path("inputContract").get(0);
            declaration.put("required", true); declaration.remove("default");
        } else {
            policy.putArray("inputContract");
            ((ObjectNode) policy.path("initialize")).remove("requestData");
        }
        long definition = publish(source);
        long id = tx().execute(t -> requests.create(marker, required ? null : "{}", marker, Instant.now())).id();
        assertThatThrownBy(() -> runtime.start(new SandboxStart(UUID.randomUUID(), definition, id, 0), context(false)))
                .isInstanceOf(WorkflowExecutionException.class);
        assertThat(requests.find(id).instanceId()).isNull();
        assertThat(requests.find(id).lockVersion()).isZero();
        assertThat(requests.find(id).status()).isEqualTo("DRAFT");
    }

    @ParameterizedTest @ValueSource(strings = {"APPROVE", "REJECT"})
    void selectedOutcomeUpdatesHostStatusAndSuccessfulReplayDoesNotWriteAgain(String outcome) throws Exception {
        long definition = publish(fixture());
        String data = "{\"amount\":9007199254740993.00,\"booking\":{\"reference\":\"TEST-001\"},\"items\":[true,null]}";
        long requestId = tx().execute(t -> requests.create(marker, data, marker, Instant.now())).id();
        var command = new SandboxStart(UUID.randomUUID(), definition, requestId, 0);
        var started = start(command);
        assertThat(requests.find(requestId).requestDataJson()).isEqualTo(data);
        assertThat(variableJson(started.instance().id())).isEqualTo(data);
        assertThat(runtime.replayStart(command, context(false))).isPresent();
        // A test-only source change proves the accepted variable is a persisted snapshot.
        // There is deliberately no supported API for editing the source.
        jdbc.update("UPDATE wf_test.sandbox_request SET request_data_json='{}' WHERE id=?", requestId);
        assertThat(variableJson(started.instance().id())).isEqualTo(data);
        jdbc.update("UPDATE wf_test.sandbox_request SET request_data_json=? WHERE id=?", data, requestId);
        assertThat(requests.find(requestId).status()).isEqualTo("IN_REVIEW");
        assertThat(requests.find(requestId).lockVersion()).isEqualTo(2);
        assertThat(runtime.start(command, context(false)).replayed()).isTrue();
        assertThat(requests.find(requestId).lockVersion()).isEqualTo(2);
        assertThatThrownBy(() -> tx().executeWithoutResult(t -> requests.updateStatus(
                requestId, started.instance().id(), 1, "IN_REVIEW", "APPROVED")))
                .isInstanceOf(io.github.leontecristian.workflow.testhost.exception.console.SandboxRequestConflict.class);
        assertThatThrownBy(() -> tx().executeWithoutResult(t -> requests.updateStatus(
                requestId, started.instance().id() + 1, 2, "IN_REVIEW", "APPROVED")))
                .isInstanceOf(io.github.leontecristian.workflow.testhost.exception.console.SandboxRequestConflict.class);
        assertThat(requests.find(requestId).status()).isEqualTo("IN_REVIEW");
        // A repeated same-status node is a no-op even when explicitly executed in a transaction.
        tx().executeWithoutResult(t -> action.execute(new ServiceActionContext(UUID.randomUUID(), started.instance().id(),
                definition, "NOOP", "SANDBOX_REQUEST", Long.toString(requestId), context(false)),
                Map.of("status", new WorkflowValue(VariableValueType.STRING, new TextLiteral("IN_REVIEW")))));
        assertThat(requests.find(requestId).lockVersion()).isEqualTo(2);
        var completion = complete(started, outcome);
        var result = runtime.complete(completion, context(true));
        String target = outcome.equals("APPROVE") ? "APPROVED" : "REJECTED";
        assertThat(result.execution().instance().resultKey()).isEqualTo(target);
        assertThat(requests.find(requestId).status()).isEqualTo(target);
        assertThat(requests.find(requestId).requestDataJson()).isEqualTo(data);
        assertThat(variableJson(started.instance().id())).isEqualTo(data);
        assertThat(requests.find(requestId).lockVersion()).isEqualTo(3);
        int events = count("wf_test.workflow_event_outbox", started.instance().id());
        assertThat(runtime.complete(completion, context(true)).replayed()).isTrue();
        assertThat(requests.find(requestId).lockVersion()).isEqualTo(3);
        assertThat(count("wf_test.workflow_event_outbox", started.instance().id())).isEqualTo(events);
        var history = jdbc.queryForList("SELECT details_json FROM wf.workflow_history WHERE instance_id=? AND event_type='SERVICE_TASK_COMPLETED' ORDER BY sequence_number", String.class, started.instance().id());
        assertThat(history).hasSize(2);
        var detail = json.readTree(history.get(1));
        assertThat(detail.path("nodeKey").asText()).isEqualTo("SET_" + target);
        assertThat(detail.path("outputs").path("previousStatus").asText()).isEqualTo("IN_REVIEW");
        assertThat(detail.path("outputs").path("status").asText()).isEqualTo(target);
        assertThat(reads.findInstance(started.instance().id()).orElseThrow().lockVersion())
                .isEqualTo(result.execution().instance().lockVersion());
    }

    @Test void downstreamFailureRollsBackStatusVersionTaskHistoryReceiptAndOutbox() throws Exception {
        var source = fixture();
        ((ArrayNode) source.path("variables")).addObject().put("key", "missing").put("type", "STRING").put("nullable", true);
        ((ArrayNode) source.path("nodes")).addObject().put("key", "FAIL_ROUTE").put("name", "Intentional failure").put("type", "EXCLUSIVE_GATEWAY");
        for (var edge : source.path("transitions")) if (edge.path("key").asText().equals("approved")) ((ObjectNode) edge).put("to", "FAIL_ROUTE");
        var edges = (ArrayNode) source.path("transitions");
        edges.addObject().put("key", "fail").put("from", "FAIL_ROUTE").put("to", "APPROVED").put("condition", "#vars.missing == 'x'");
        edges.addObject().put("key", "fallback").put("from", "FAIL_ROUTE").put("to", "APPROVED").put("default", true);
        long definition = publish(source), requestId = request();
        var started = start(new SandboxStart(UUID.randomUUID(), definition, requestId, 0));
        assertThat(variableJson(started.instance().id())).isEqualTo("{}");
        long instance = started.instance().id();
        int history = count("wf.workflow_history", instance), events = count("wf_test.workflow_event_outbox", instance);
        var completion = complete(started, "APPROVE");
        assertThatThrownBy(() -> runtime.complete(completion, context(true)))
                .isInstanceOfSatisfying(WorkflowExecutionException.class,
                        failure -> assertThat(failure.category()).isEqualTo(WorkflowExecutionException.Category.UNINITIALIZED_VARIABLE));
        assertThat(requests.find(requestId).status()).isEqualTo("IN_REVIEW");
        assertThat(requests.find(requestId).lockVersion()).isEqualTo(2);
        assertThat(reads.findTask(started.task().id()).orElseThrow().status()).isEqualTo(TaskStatus.OPEN);
        assertThat(reads.findTaskSubmission(started.task().id())).isEmpty();
        assertThat(reads.findInstance(instance).orElseThrow().lockVersion()).isEqualTo(started.instance().lockVersion());
        assertThat(count("wf.workflow_history", instance)).isEqualTo(history);
        assertThat(count("wf_test.workflow_event_outbox", instance)).isEqualTo(events);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_command_receipt WHERE instance_id=? AND command_id=?", Integer.class, instance, completion.commandId())).isZero();
    }

    @Test void invalidStartTransitionRollsBackAllocatedLinkAndStatus() throws Exception {
        var source = fixture();
        ((ObjectNode) source.path("startPolicy").path("initialize").path("statusInReview")).put("value", "APPROVED");
        long definition = publish(source);
        long requestId = tx().execute(t -> requests.create(marker, "{\"amount\":123.00}", marker, Instant.now())).id();
        assertThatThrownBy(() -> runtime.start(new SandboxStart(UUID.randomUUID(), definition, requestId, 0), context(false)))
                .isInstanceOfSatisfying(WorkflowExecutionException.class,
                        failure -> assertThat(failure.category()).isEqualTo(WorkflowExecutionException.Category.SERVICE_ACTION_FAILED));
        assertThat(requests.find(requestId).status()).isEqualTo("DRAFT");
        assertThat(requests.find(requestId).lockVersion()).isZero();
        assertThat(requests.find(requestId).instanceId()).isNull();
    }
}
