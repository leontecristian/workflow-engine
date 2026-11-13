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

import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Binding;
import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue;
import io.github.leontecristian.workflow.core.dto.runtime.TaskClaimPolicy;
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

    @Test void omittedOutputDoesNotCreateRowAndAcceptedEmptyObjectDoes() {
        var human = namedProducer();
        var model = namedModel(human, Map.of());
        assertTrue(variables.afterTaskCompletion(1,model,human,null,3,4,batch).isEmpty());
        assertTrue(batch.events().isEmpty());
        verifyNoInteractions(em);

        var accepted = CompleteWorkflowTask.parseResult("{}");
        var state = variables.afterTaskCompletion(1,model,human,accepted,3,4,batch);
        assertEquals(accepted,state.get("copy").value());
        var persisted = org.mockito.ArgumentCaptor.forClass(Object.class);
        org.mockito.Mockito.verify(em,org.mockito.Mockito.times(2)).persist(persisted.capture());
        var fact = (WorkflowHistoryEntity) persisted.getAllValues().get(0);
        assertEquals(3L,fact.getActivityId());
        assertEquals(4L,fact.getTaskId());
        var details = json.tree(fact.getDetailsJson());
        assertEquals("review",details.path("nodeKey").asText());
        var write = details.path("writes").get(0);
        assertFalse(write.path("previousInitialized").asBoolean());
        assertEquals("taskResult",write.path("source").path("source").asText());
        assertEquals("review",write.path("source").path("key").asText());
        assertEquals("{}",write.path("value").toString());
        assertEquals(accepted,((WorkflowVariableEntity) persisted.getAllValues().get(1)).executionValue().value());
    }

    @Test void allResolutionCreatesActivityCollectionOnceAndOrdinaryReplacementUpdatesItsRow() {
        var single = namedProducer();
        var human = new MinimalWorkflowDefinition.Human(single.key(),single.taskType(),single.assignment(),
                single.outcomes(),single.claimPolicy(),single.resultContract(),single.outputVariable(),single.inputs(),
                io.github.leontecristian.workflow.core.dto.runtime.CompletionPolicy.ALL,"APPROVE");
        var model = namedModel(human,Map.of());
        var original = WorkflowJsonValue.parse("[{\"taskId\":\"11\",\"taskKey\":\"finance\",\"outcomeKey\":\"APPROVE\",\"comment\":null,\"result\":{\"amount\":1000.00}},{\"taskId\":\"12\",\"taskKey\":\"legal\",\"outcomeKey\":\"REJECT\",\"comment\":null}]");
        var persisted = new ArrayList<Object>();
        doAnswer(call -> { persisted.add(call.getArgument(0)); return null; }).when(em).persist(any());
        var state = variables.afterTaskResolution(1,model,human,original,3,null,batch);
        assertEquals(2,persisted.size());
        var fact = (WorkflowHistoryEntity) persisted.get(0);
        var row = (WorkflowVariableEntity) persisted.get(1);
        assertEquals("VARIABLES_WRITTEN",fact.getEventType());
        assertEquals(Short.valueOf((short)3),fact.getDetailsSchemaVersion());
        assertEquals(3L,fact.getActivityId()); assertEquals(null,fact.getTaskId());
        var write = json.tree(fact.getDetailsJson()).path("writes").get(0);
        assertEquals(json.tree("{\"source\":\"activitySubmissions\",\"nodeKey\":\"review\"}"),write.path("source"));
        assertEquals(json.tree(original.toJson()),write.path("value"));
        assertFalse(write.path("value").get(1).has("result"));
        assertFalse(write.path("previousInitialized").asBoolean());
        assertEquals(original,row.executionValue().value());
        assertEquals(original,state.get("copy").value());

        when(repository.findById(row.getId())).thenReturn(Optional.of(row));
        clearInvocations(em);
        assertEquals(Category.CONFLICT,assertThrows(WorkflowExecutionException.class,
                () -> variables.afterTaskResolution(1,model,human,original,3,null,batch)).category());
        verifyNoInteractions(em);
        assertEquals(1,batch.last());
        assertEquals(original,row.executionValue().value());

        var replacement = WorkflowJsonValue.parse("{\"working\":true}");
        variables.apply(1,"later",Map.of("copy",new Binding("actionOutput","replacement",null)),state,
                Map.of("copy",new WorkflowValue(VariableValueType.JSON,replacement)),batch);
        assertEquals(replacement,row.executionValue().value());
        assertEquals(replacement,state.get("copy").value());
        assertEquals(2,batch.last());
        assertEquals(3,persisted.size()); // Two history facts, only the original variable insertion.
        assertEquals(1L,persisted.stream().filter(WorkflowVariableEntity.class::isInstance).count());
        var replacementFact = (WorkflowHistoryEntity) persisted.get(2);
        assertEquals(Short.valueOf((short)2),replacementFact.getDetailsSchemaVersion());
        var replacementWrite = json.tree(replacementFact.getDetailsJson()).path("writes").get(0);
        assertTrue(replacementWrite.path("previousInitialized").asBoolean());
        assertEquals(json.tree(original.toJson()),replacementWrite.path("previousValue"));
        assertEquals(json.tree(original.toJson()),json.tree(fact.getDetailsJson()).path("writes").get(0).path("value"));
    }

    @Test void completionCopyNeverOverwritesAnExistingRow() {
        var human = namedProducer();
        var existing = new WorkflowVariableEntity(1,"copy",new WorkflowValue(VariableValueType.JSON,
                CompleteWorkflowTask.parseResult("{\"amount\":1.200}")));
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        assertEquals(Category.CONFLICT, assertThrows(WorkflowExecutionException.class,
                () -> variables.afterTaskCompletion(1,namedModel(human, Map.of()),human,
                        CompleteWorkflowTask.parseResult("{}"),3,4,batch)).category());
        assertEquals("{\"amount\":1.200}",((WorkflowJsonValue) existing.executionValue().value()).toJson());
        assertTrue(batch.events().isEmpty());
        verifyNoInteractions(em);
    }

    @Test void completionCopyConsumesExistingStateBudget() {
        var human = namedProducer();
        var declarations = new TreeMap<String,ActionField>();
        var existing = new ArrayList<WorkflowVariableEntity>();
        String large = "{\"text\":\"" + "x".repeat(3953) + "\"}";
        for (int i = 0; i < 66; i++) {
            String key = "value" + i;
            declarations.put(key,new ActionField(key,VariableValueType.JSON,false,false));
            var value = WorkflowJsonValue.parse("[" + String.join(",",java.util.Collections.nCopies(20,large)) + "]");
            existing.add(new WorkflowVariableEntity(1,key,new WorkflowValue(VariableValueType.JSON,value)));
        }
        when(repository.findByInstanceId(1,org.springframework.data.domain.PageRequest.of(0,101))).thenReturn(existing);
        var model = namedModel(human,declarations);
        RuntimeValueBindings.tokens(variables.read(1,model),json); // Existing state fits before the copy.
        var accepted = CompleteWorkflowTask.parseResult("{\"items\":[" +
                String.join(",",java.util.Collections.nCopies(3,large)) + "]}");
        assertEquals(Category.LIMIT_EXCEEDED, assertThrows(WorkflowExecutionException.class,
                () -> variables.afterTaskCompletion(1,model,human,accepted,3,4,batch)).category());
        assertTrue(batch.events().isEmpty());
        org.mockito.Mockito.verify(em,org.mockito.Mockito.never()).persist(any());
    }

    private MinimalWorkflowDefinition.Human namedProducer() {
        return new MinimalWorkflowDefinition.Human("review","REVIEW",new MinimalWorkflowDefinition.Assignment("resolver"),
                List.of(),TaskClaimPolicy.OPTIONAL,
                new io.github.leontecristian.workflow.core.dto.definition.document.TaskResultContract(
                        io.github.leontecristian.workflow.core.dto.definition.document.TaskResultContract.Mode.OPTIONAL,"{\"type\":\"object\"}"),"copy");
    }

    private MinimalWorkflowDefinition namedModel(MinimalWorkflowDefinition.Human human, Map<String,ActionField> ordinary) {
        var fields = new TreeMap<>(ordinary);
        fields.put("copy",new ActionField("copy",VariableValueType.JSON,false,false));
        return new MinimalWorkflowDefinition(null,Map.of(human.key(),human),null,List.of(),Map.of(),fields,
                Map.of(),Map.of(),Map.of(),java.util.Set.of(),Map.of("copy",human.key()));
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
        for (int i = 0; i < 30; i++) writes.put("v" + i, new WorkflowValue(VariableValueType.JSON,
                WorkflowJsonValue.parse("[" + String.join(",", java.util.Collections.nCopies(12,
                        "\"" + "x".repeat(16000) + "\"")) + "]")));
        assertEquals(Category.LIMIT_EXCEEDED, assertThrows(WorkflowExecutionException.class,
                () -> variables.apply(1, "LARGE", bindings(writes), state, writes, batch)).category());
        assertTrue(state.isEmpty());
        assertTrue(batch.events().isEmpty());
        verifyNoInteractions(repository, em);
    }

    @Test void cumulativeBudgetFailureDoesNotMutateExistingVariableOrSnapshot() {
        for (int i = 0; i < 559; i++) batch.next("TEST", null, null, null, Map.of("value", "x".repeat(60000)), Map.of());
        var state = new TreeMap<String, WorkflowValue>();
        var writes = new HashMap<String, WorkflowValue>();
        for (int i = 0; i < 15; i++) { state.put("v" + i, text("x".repeat(100))); writes.put("v" + i, text("y".repeat(4000))); }
        var original = Map.copyOf(state);
        clearInvocations(em);
        assertEquals(Category.LIMIT_EXCEEDED, assertThrows(WorkflowExecutionException.class,
                () -> variables.apply(1, "OVERFLOW", bindings(writes), state, writes, batch)).category());
        assertEquals(original, state);
        verifyNoInteractions(repository, em);
        assertEquals(559, batch.last());
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
