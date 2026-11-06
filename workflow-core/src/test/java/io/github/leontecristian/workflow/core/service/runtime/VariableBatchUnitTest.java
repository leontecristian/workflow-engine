package io.github.leontecristian.workflow.core.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Binding;
import io.github.leontecristian.workflow.core.dto.runtime.ActionField;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.NullLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
import io.github.leontecristian.workflow.core.dto.runtime.TextLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowHistoryEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowVariableEntity;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowVariableRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure persistence doubles; SQL rollback is verified separately in the guarded host suite. */
class VariableBatchUnitTest {
    private final EntityManager em = mock(EntityManager.class);
    private final WorkflowVariableRepository repository = mock(WorkflowVariableRepository.class);
    private final RuntimeJson json = new RuntimeJson();
    private final RuntimeVariables variables = new RuntimeVariables(repository, em, json);
    private final RuntimeHistoryBatch batch = new RuntimeHistoryBatch(em, json, 1, 2, UUID.randomUUID(),
            new WorkflowCommandContext(ActorKind.USER, "batch-test", Instant.EPOCH, "batch-test"));

    private WorkflowValue text(String value) { return new WorkflowValue(VariableValueType.STRING, new TextLiteral(value)); }
    private Map<String, Binding> bindings(Map<String, WorkflowValue> writes) {
        var result = new TreeMap<String, Binding>();
        writes.keySet().forEach(key -> result.put(key, new Binding("actionOutput", key, null)));
        return result;
    }

    @Test void oneSortedBatchRetainsExactNullMissingAndUnchangedEvidence() {
        var previous = new TreeMap<String, WorkflowValue>();
        previous.put("same", text("unchanged"));
        previous.put("nullValue", new WorkflowValue(VariableValueType.STRING, NullLiteral.INSTANCE));
        var writes = new LinkedHashMap<String, WorkflowValue>();
        writes.put("same", text("unchanged"));
        writes.put("quantity", new WorkflowValue(VariableValueType.LONG, new TextLiteral("9007199254740993")));
        writes.put("nullValue", previous.get("nullValue"));
        var persisted = new ArrayList<Object>();
        doAnswer(call -> { persisted.add(call.getArgument(0)); return null; }).when(em).persist(any());
        when(repository.findById(any())).thenReturn(Optional.empty());

        variables.apply(1, "BEFORE", bindings(writes), previous, writes, batch);

        assertEquals(1, batch.last());
        assertEquals(1, persisted.stream().filter(WorkflowHistoryEntity.class::isInstance).count());
        assertEquals(3, persisted.stream().filter(WorkflowVariableEntity.class::isInstance).count());
        var history = (WorkflowHistoryEntity) persisted.get(0);
        assertEquals("VARIABLES_WRITTEN", history.getEventType());
        assertEquals(1, batch.events().get(0).eventSchemaVersion());
        assertEquals(Map.of("nodeKey", "BEFORE", "writeCount", "3"), batch.events().get(0).payload());
        var details = json.tree(history.getDetailsJson());
        assertEquals("BEFORE", details.path("nodeKey").asText());
        var entries = details.path("writes");
        assertEquals(List.of("nullValue", "quantity", "same"),
                java.util.stream.StreamSupport.stream(entries.spliterator(), false).map(n -> n.path("variableKey").asText()).toList());
        assertTrue(entries.get(0).path("previousInitialized").asBoolean());
        assertTrue(entries.get(0).path("previousValue").isNull());
        assertTrue(entries.get(0).path("value").isNull());
        assertFalse(entries.get(1).path("previousInitialized").asBoolean());
        assertTrue(entries.get(1).path("previousValue").isNull());
        assertEquals("9007199254740993", entries.get(1).path("value").asText());
        assertEquals("actionOutput", entries.get(1).path("source").path("source").asText());
        assertEquals("unchanged", entries.get(2).path("previousValue").asText());
        assertEquals(writes, previous);
    }

    @Test void preparedSwapUsesOneBeforeSnapshotAndUpdatesExistingRows() {
        var state = new TreeMap<>(Map.of("a", text("first"), "b", text("second")));
        var mappings = Map.of("a", new Binding("variable", "b", null), "b", new Binding("variable", "a", null));
        var field = new ActionField("a", VariableValueType.STRING, false, false);
        var writes = RuntimeValueBindings.prepare(mappings, Map.of("a", field, "b", field), state, Map.of(), Category.INVALID_ACTION_OUTPUT);
        var a = new WorkflowVariableEntity(1, "a", state.get("a"));
        var b = new WorkflowVariableEntity(1, "b", state.get("b"));
        when(repository.findById(any())).thenAnswer(call -> Optional.of(call.getArgument(0).equals(a.getId()) ? a : b));
        variables.apply(1, "SWAP", mappings, state, writes, batch);
        assertEquals(text("second"), a.executionValue());
        assertEquals(text("first"), b.executionValue());
        assertEquals(1, batch.last());
    }

    @Test void allMissingOptionalSourcesAndEmptyOperationsProduceNoFactOrMutation() {
        var state = new TreeMap<>(Map.of("existing", text("keep")));
        var mapping = Map.of("optional", new Binding("actionOutput", "optional", null));
        var declarations = Map.of("optional", new ActionField("optional", VariableValueType.STRING, false, true));
        var writes = RuntimeValueBindings.prepare(mapping, declarations, state, Map.of(), Category.INVALID_ACTION_OUTPUT);
        variables.apply(1, "EMPTY", mapping, state, writes, batch);
        variables.apply(1, "EMPTY", Map.of(), state, Map.of(), batch);
        assertEquals(Map.of("existing", text("keep")), state);
        assertTrue(batch.events().isEmpty());
        verifyNoInteractions(repository, em);
    }

    @Test void omittedOptionalOutputIsExcludedFromNonemptyBatch() {
        var declarations = Map.of("a", new ActionField("a", VariableValueType.STRING, false, true),
                "b", new ActionField("b", VariableValueType.STRING, false, true));
        var mappings = Map.of("a", new Binding("actionOutput", "a", null), "b", new Binding("actionOutput", "b", null));
        var state = new TreeMap<String, WorkflowValue>();
        var writes = RuntimeValueBindings.prepare(mappings, declarations, state, Map.of("a", text("present")), Category.INVALID_ACTION_OUTPUT);
        when(repository.findById(any())).thenReturn(Optional.empty());
        variables.apply(1, "OUTPUT", mappings, state, writes, batch);
        assertEquals(Map.of("a", text("present")), state);
        assertEquals("1", batch.events().get(0).payload().get("writeCount"));
    }

    @Test void oversizedCombinedEvidenceFailsBeforeAnyVariableOrHistoryMutation() {
        var state = new TreeMap<String, WorkflowValue>();
        var writes = new TreeMap<String, WorkflowValue>();
        for (int i = 0; i < 30; i++) writes.put("v" + i, text("\u0000".repeat(4000)));
        assertEquals(Category.LIMIT_EXCEEDED, assertThrows(WorkflowExecutionException.class,
                () -> variables.apply(1, "LARGE", bindings(writes), state, writes, batch)).category());
        assertTrue(state.isEmpty());
        assertTrue(batch.events().isEmpty());
        verifyNoInteractions(repository, em);
    }

    @Test void cumulativeBudgetFailureDoesNotMutateExistingVariableOrSnapshot() {
        for (int i = 0; i < 139; i++) batch.next("TEST", null, null, null, Map.of("value", "x".repeat(60000)), Map.of());
        var state = new TreeMap<String, WorkflowValue>();
        var writes = new HashMap<String, WorkflowValue>();
        for (int i = 0; i < 15; i++) { state.put("v" + i, text("x".repeat(100))); writes.put("v" + i, text("y".repeat(4000))); }
        var original = Map.copyOf(state);
        clearInvocations(em);
        assertEquals(Category.LIMIT_EXCEEDED, assertThrows(WorkflowExecutionException.class,
                () -> variables.apply(1, "OVERFLOW", bindings(writes), state, writes, batch)).category());
        assertEquals(original, state);
        verifyNoInteractions(repository, em);
        assertEquals(139, batch.last());
    }

    @Test void eachOperationGetsItsOwnSequenceAndBatch() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        var state = new TreeMap<String, WorkflowValue>();
        var writes = Map.of("a", text("one"), "b", text("two"));
        variables.apply(1, "FIRST", bindings(writes), state, writes, batch);
        variables.apply(1, "SECOND", bindings(writes), state, writes, batch);
        assertEquals(2, batch.last());
        assertEquals(List.of("FIRST", "SECOND"), batch.events().stream().map(e -> e.payload().get("nodeKey")).toList());
    }

    @Test void maximumVariableCountStillProducesOneFact() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        var state = new TreeMap<String, WorkflowValue>();
        var writes = new TreeMap<String, WorkflowValue>();
        for (int i = 0; i < 100; i++) writes.put("value" + i, text("small"));
        variables.apply(1, "START", bindings(writes), state, writes, batch);
        assertEquals(100, state.size());
        assertEquals(1, batch.last());
        assertEquals("100", batch.events().get(0).payload().get("writeCount"));
    }
}
