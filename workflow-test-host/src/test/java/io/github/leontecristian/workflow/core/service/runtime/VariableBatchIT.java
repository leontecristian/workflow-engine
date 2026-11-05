package io.github.leontecristian.workflow.core.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Binding;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.ScalarValue;
import io.github.leontecristian.workflow.core.dto.runtime.TextLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.entity.definition.WorkflowDefinitionEntity;
import io.github.leontecristian.workflow.core.entity.definition.WorkflowDefinitionVersionEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowInstanceEntity;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.service.definition.DefinitionContentCanonicalizer;
import io.github.leontecristian.workflow.testhost.configuration.integration.DatabaseIntegrationTest;
import io.github.leontecristian.workflow.testhost.repository.event.ConsoleEventStore;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Real component transaction tests, not end-to-end workflow or concurrent-command acceptance. */
class VariableBatchIT extends DatabaseIntegrationTest {
    @Autowired private EntityManager em;
    @Autowired private RuntimeVariables variables;
    @Autowired private RuntimeJson json;
    @Autowired private WorkflowRuntimeQueryService reads;
    @Autowired private ConsoleEventStore outbox;
    @Autowired private ConsoleWire wire;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    private final String marker = "batch-it-" + UUID.randomUUID();
    private final String correlation = UUID.randomUUID().toString();
    private Long definitionId;
    private Long versionId;
    private Long instanceId;

    @BeforeEach void allocateOwnedFixture() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var now = LocalDateTime.of(2026, 11, 5, 0, 0);
            var definition = new WorkflowDefinitionEntity(marker, marker, null, now, marker);
            em.persist(definition); definitionId = definition.getId();
            var version = new WorkflowDefinitionVersionEntity(definitionId, 1, "{}",
                    DefinitionContentCanonicalizer.hash("{}"), DefinitionContentCanonicalizer.IDENTIFIER, now, marker);
            em.persist(version); versionId = version.getId();
            var instance = new WorkflowInstanceEntity(versionId, null, null, "START", now, marker);
            em.persist(instance); instanceId = instance.getId();
        });
    }

    @AfterEach void removeOnlyOwnedFixture() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            if (instanceId != null) {
                jdbc.update("DELETE FROM wf_test.workflow_event_outbox WHERE instance_id=?", instanceId);
                jdbc.update("DELETE FROM wf.workflow_history WHERE instance_id=?", instanceId);
                jdbc.update("DELETE FROM wf.workflow_variable WHERE instance_id=?", instanceId);
                jdbc.update("DELETE FROM wf.workflow_instance WHERE id=?", instanceId);
            }
            jdbc.update("DELETE FROM wf_test.management_audit WHERE correlation_id=? AND actor_ref=?", correlation, marker);
            if (versionId != null) jdbc.update("DELETE FROM wf.workflow_definition_version WHERE id=?", versionId);
            if (definitionId != null) jdbc.update("DELETE FROM wf.workflow_definition WHERE id=? AND workflow_key=?", definitionId, marker);
        });
    }

    private void apply(Map<String, ScalarValue> writes, boolean failAfterHandoff) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var instance = em.find(WorkflowInstanceEntity.class, instanceId);
            long previous = instance.getLastHistorySequence();
            instance.reserveHistorySequence(); em.flush();
            jdbc.update("INSERT INTO wf_test.management_audit "
                    + "(occurred_at,actor_ref,operation,entity_kind,entity_id,correlation_id,evidence_json) "
                    + "VALUES (SYSUTCDATETIME(),?,'TEST','TASK_TYPE',1,?,N'{}')", marker, correlation);
            var context = new WorkflowCommandContext(ActorKind.USER, marker, Instant.parse("2026-11-05T00:00:00Z"), marker);
            var batch = new RuntimeHistoryBatch(em, json, instanceId, versionId, UUID.randomUUID(), context);
            batch.beginAt(previous);
            var bindings = new TreeMap<String, Binding>();
            writes.keySet().forEach(key -> bindings.put(key, new Binding("startInput", key, null)));
            variables.apply(instanceId, "START", bindings, new TreeMap<>(), writes, batch);
            for (var event : batch.events()) outbox.record(event, wire.json(event));
            em.flush();
            if (failAfterHandoff) throw new IllegalStateException("intentional batch rollback");
        });
    }

    private Map<String, ScalarValue> smallWrites() {
        var value = new ScalarValue(VariableValueType.LONG, new TextLiteral("9007199254740993"));
        return Map.of("quantity", value, "copy", value);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE instance_id=?", Integer.class, instanceId);
    }

    @Test void committedBatchHasExactVariablesOneHistoryAndPrivateDurableSummary() throws Exception {
        apply(smallWrites(), false);
        assertEquals(2, count("wf.workflow_variable"));
        assertEquals(1, count("wf.workflow_history"));
        assertEquals(1, count("wf_test.workflow_event_outbox"));
        assertTrue(jdbc.queryForList("SELECT long_value FROM wf.workflow_variable WHERE instance_id=?", Long.class, instanceId)
                .stream().allMatch(value -> value == 9007199254740993L));
        var fact = reads.findHistoryEvent(instanceId, 1).orElseThrow();
        assertEquals("VARIABLES_WRITTEN", fact.envelope().eventType());
        var details = json.tree(fact.detailsJson());
        assertEquals(2, details.path("writes").size());
        assertEquals("copy", details.path("writes").get(0).path("variableKey").asText());
        assertEquals("9007199254740993", details.path("writes").get(1).path("value").asText());
        String stored = jdbc.queryForObject("SELECT event_json FROM wf_test.workflow_event_outbox WHERE instance_id=?", String.class, instanceId);
        var payload = new com.fasterxml.jackson.databind.ObjectMapper().readTree(stored).path("payload");
        assertEquals(2, payload.size());
        assertEquals("2", payload.path("writeCount").asText());
        assertEquals("START", payload.path("nodeKey").asText());
    }

    @Test void laterHostFailureRollsBackBatchVariablesOutboxAuditAndAggregateClaim() {
        assertThrows(IllegalStateException.class, () -> apply(smallWrites(), true));
        assertRolledBack();
    }

    @Test void oversizedCombinedBatchRollsBackPriorHostEffectAndAggregateClaim() {
        var writes = new TreeMap<String, ScalarValue>();
        for (int i = 0; i < 20; i++) writes.put("value" + i,
                new ScalarValue(VariableValueType.STRING, new TextLiteral("x".repeat(4000))));
        var error = assertThrows(WorkflowExecutionException.class, () -> apply(writes, false));
        assertEquals(WorkflowExecutionException.Category.LIMIT_EXCEEDED, error.category());
        assertRolledBack();
    }

    private void assertRolledBack() {
        assertEquals(0, count("wf.workflow_variable"));
        assertEquals(0, count("wf.workflow_history"));
        assertEquals(0, count("wf_test.workflow_event_outbox"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wf_test.management_audit WHERE correlation_id=?", Integer.class, correlation));
        assertEquals(0L, jdbc.queryForObject("SELECT last_history_sequence FROM wf.workflow_instance WHERE id=?", Long.class, instanceId));
        assertEquals(0L, jdbc.queryForObject("SELECT lock_version FROM wf.workflow_instance WHERE id=?", Long.class, instanceId));
    }
}
