package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.NullLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.StartInputs;
import io.github.leontecristian.workflow.core.dto.runtime.StartWorkflow;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowVariablePage;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowVariableView;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowVariableEntity;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class JsonExecutionUnitTest {
    @Test void canonicalIdentityPreservesScaleShapeAndUnicodeOrdering() {
        var value=WorkflowJsonValue.parse(" {\"z\":[1,1.00,0.00,1e2,1E-10],\"a\":{\"x\":null}} ");
        assertThat(value.toJson()).isEqualTo("{\"a\":{\"x\":null},\"z\":[1,1.00,0.00,1E+2,1E-10]}");
        assertThat(WorkflowJsonValue.parse(value.toJson())).isEqualTo(value);
        assertThat(WorkflowJsonValue.parse("{\"n\":1}")).isNotEqualTo(WorkflowJsonValue.parse("{\"n\":1.00}"));
        assertThat(WorkflowJsonValue.parse("{}")).isNotEqualTo(WorkflowJsonValue.parse("{\"n\":null}"));
        assertThat(WorkflowJsonValue.parse("{\"b\":-0.00,\"a\":1e2}").toJson()).isEqualTo("{\"a\":1E+2,\"b\":0.00}");
        assertThat(WorkflowJsonValue.parse("{\"😀\":1,\"\uE000\":2}").toJson()).startsWith("{\"\uE000\":2");
    }
    @Test void strictParserRejectsInvalidAndUnrepresentableDataWithoutSourceInErrors() {
        for (String input:List.of("null","1","{} []","{\"a\":1,\"a\":2}","[0.00000000001]",
                "[1e28]","[1e2147483648]","[\"\\uD800\"]","[true,]")) {
            assertThatThrownBy(()->WorkflowJsonValue.parse(input)).isInstanceOf(WorkflowExecutionException.class)
                    .satisfies(f->assertThat(f.getCause()).isNull());
        }
        assertThat(WorkflowJsonValue.parse("[9999999999999999999999999999.9999999999,1.00000000000000000000]").toJson())
                .endsWith("1.00000000000000000000]");
    }
    static String sized(int chars) {
        // Nine bounded strings, with punctuation included in the exact canonical capacity.
        StringBuilder out=new StringBuilder("["); int remaining=chars-2-8-18;
        for(int i=0;i<9;i++) { if(i>0) out.append(','); int n=Math.min(16000,remaining); out.append('"').append("x".repeat(n)).append('"'); remaining-=n; }
        return out.append(']').toString();
    }
    @Test void inclusiveValueDepthCountsAndRawIngressBounds() {
        String max=sized(128*1024);
        assertThat(WorkflowJsonValue.parse(max).toJson()).hasSize(128*1024);
        assertThatThrownBy(()->WorkflowJsonValue.parse(sized(128*1024+1))).isInstanceOf(WorkflowExecutionException.class);
        assertThat(WorkflowJsonValue.parse("[".repeat(16)+"0"+"]".repeat(16))).isNotNull();
        assertThatThrownBy(()->WorkflowJsonValue.parse("[".repeat(17)+"0"+"]".repeat(17))).isInstanceOf(WorkflowExecutionException.class);
        assertThat(WorkflowJsonValue.parse("["+"null,".repeat(999)+"null]")).isNotNull();
        assertThatThrownBy(()->WorkflowJsonValue.parse("["+"null,".repeat(1000)+"null]")).isInstanceOf(WorkflowExecutionException.class);
        assertThatThrownBy(()->WorkflowJsonValue.parse(" ".repeat(2*1024*1024)+"[]")).isInstanceOf(WorkflowExecutionException.class);
        assertThat(WorkflowJsonValue.parse("[]"+" ".repeat(2*1024*1024-2)).toJson()).isEqualTo("[]");
    }
    @Test void replacementAndTypedNullUseOneExistingStorageColumn() {
        var row=new WorkflowVariableEntity(1,"booking",new WorkflowValue(VariableValueType.JSON,WorkflowJsonValue.parse("{\"n\":1.00,\"obsolete\":true}")));
        assertThat(row.executionValue().value()).isEqualTo(WorkflowJsonValue.parse(row.getJsonValue()));
        row.replace(new WorkflowValue(VariableValueType.JSON,WorkflowJsonValue.parse("{\"n\":2.00}")));
        assertThat(row.getJsonValue()).isEqualTo("{\"n\":2.00}");
        row.replace(new WorkflowValue(VariableValueType.JSON,NullLiteral.INSTANCE));
        assertThat(row.getJsonValue()).isNull(); assertThat(row.executionValue().value()).isEqualTo(NullLiteral.INSTANCE);
    }
    @Test void requestFingerprintUsesWholeExactInputBeforeDefaults() {
        var json=new RuntimeJson(); var command=new StartWorkflow(UUID.randomUUID(),42,null,null,null);
        var context=new WorkflowCommandContext(ActorKind.USER,"tester",Instant.EPOCH,null);
        var a=new StartInputs(Map.of("booking",WorkflowJsonValue.parse("{\"a\":1.00,\"b\":[null]}")));
        var b=new StartInputs(Map.of("booking",WorkflowJsonValue.parse("{ \"b\": [null], \"a\":1.00 }")));
        assertThat(json.fingerprint(command,a,context)).isEqualTo(json.fingerprint(command,b,context));
        for(String changed:List.of("{\"a\":1,\"b\":[null]}","{\"a\":1.00,\"b\":[]}","{\"a\":1.00}"))
            assertThat(json.fingerprint(command,a,context)).isNotEqualTo(json.fingerprint(command,new StartInputs(Map.of("booking",WorkflowJsonValue.parse(changed))),context));
    }
    @Test void fullSizeHistoryDoesNotUseReceiptCeilingAndPagesUseRawValues() {
        var json=new RuntimeJson(); var value=WorkflowJsonValue.parse(sized(128*1024));
        var tokens=RuntimeValueBindings.tokens(Map.of("booking",new WorkflowValue(VariableValueType.JSON,value)),json);
        String history=json.history(Map.of("before",tokens,"after",tokens));
        assertThat(json.tree(history).path("after").path("booking").isArray()).isTrue();
        assertThatThrownBy(()->json.evidence(tokens)).isInstanceOf(WorkflowExecutionException.class);
        String page=json.variablePage(new WorkflowVariablePage(1,2,List.of(new WorkflowVariableView("booking",VariableValueType.JSON,true,new WorkflowValue(VariableValueType.JSON,value))),null));
        assertThat(json.tree(page).path("items").get(0).path("value").isArray()).isTrue();
    }
    @Test void exactCurrentStateAndStartServiceEnvelopeBoundariesIncludeKeysAndPunctuation() {
        var json=new RuntimeJson(); var values=new java.util.TreeMap<String,WorkflowValue>();
        for(String key:List.of("a","b","c")) values.put(key,new WorkflowValue(VariableValueType.JSON,WorkflowJsonValue.parse(sized(128*1024))));
        values.put("d",new WorkflowValue(VariableValueType.JSON,WorkflowJsonValue.parse(sized(128*1024-21))));
        String maximum=json.encode(RuntimeValueBindings.tokens(values,json));
        assertThat(maximum.length()*2).isEqualTo(1024*1024);
        assertThatCode(()->RuntimeValueBindings.envelope(maximum)).doesNotThrowAnyException();
        var fields=values.keySet().stream().map(k->new io.github.leontecristian.workflow.core.dto.runtime.ActionField(k,VariableValueType.JSON,true,false)).toList();
        assertThatCode(()->RuntimeValueBindings.output(fields,values,json)).doesNotThrowAnyException();
        var em=org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class);
        var repository=org.mockito.Mockito.mock(io.github.leontecristian.workflow.core.repository.runtime.WorkflowVariableRepository.class);
        org.mockito.Mockito.when(repository.findById(org.mockito.Mockito.any())).thenReturn(java.util.Optional.empty());
        var variables=new RuntimeVariables(repository,em,json);
        var batch=new RuntimeHistoryBatch(em,json,1,2,UUID.randomUUID(),new WorkflowCommandContext(ActorKind.USER,"tester",Instant.EPOCH,null));
        var bindings=new java.util.TreeMap<String,io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Binding>();
        values.keySet().forEach(k->bindings.put(k,new io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Binding("actionOutput",k,null)));
        assertThatCode(()->variables.apply(1,"MAX",bindings,new java.util.TreeMap<>(),values,batch)).doesNotThrowAnyException();
        assertThatCode(()->variables.apply(1,"REPLACE",bindings,new java.util.TreeMap<>(values),values,batch)).doesNotThrowAnyException();
        var retained=org.mockito.ArgumentCaptor.forClass(Object.class);
        org.mockito.Mockito.verify(em,org.mockito.Mockito.atLeastOnce()).persist(retained.capture());
        var replacement=retained.getAllValues().stream().filter(io.github.leontecristian.workflow.core.entity.runtime.WorkflowHistoryEntity.class::isInstance)
                .map(io.github.leontecristian.workflow.core.entity.runtime.WorkflowHistoryEntity.class::cast).reduce((first,last)->last).orElseThrow();
        assertThat(json.tree(replacement.getDetailsJson()).path("writes")).allSatisfy(write->{
            assertThat(write.path("previousInitialized").asBoolean()).isTrue();
            assertThat(write.path("previousValue")).isEqualTo(write.path("value"));
        });
        values.put("d",new WorkflowValue(VariableValueType.JSON,WorkflowJsonValue.parse(sized(128*1024-20))));
        String overflow=maximum.replaceFirst("x","xx");
        assertThat(overflow.length()*2).isEqualTo(1024*1024+2);
        assertThatThrownBy(()->RuntimeValueBindings.envelope(overflow)).isInstanceOf(WorkflowExecutionException.class);
        assertThatThrownBy(()->RuntimeValueBindings.output(fields,values,json)).isInstanceOf(WorkflowExecutionException.class);
        org.mockito.Mockito.clearInvocations(em,repository);
        assertThatThrownBy(()->variables.apply(1,"OVER",bindings,new java.util.TreeMap<>(),values,batch)).isInstanceOf(WorkflowExecutionException.class);
        org.mockito.Mockito.verifyNoInteractions(em,repository);
    }
    private String historyArray(int chars) {
        int count=(chars+16002)/16003, remaining=chars-3*count-1;
        var out=new StringBuilder("[");
        for(int i=0;i<count;i++) { if(i>0) out.append(','); int n=Math.min(16000,remaining); out.append('"').append("x".repeat(n)).append('"'); remaining-=n; }
        return out.append(']').toString();
    }
    @Test void historyAndCumulativeBudgetsAcceptExactLimitsAndStopBeforeNextPersistence() throws Exception {
        var json=new RuntimeJson(); var details=json.tree(historyArray(2*1024*1024));
        assertThat(json.history(details)).hasSize(2*1024*1024);
        var overflow=new com.fasterxml.jackson.databind.ObjectMapper().readTree(historyArray(2*1024*1024).replaceFirst("x","xx"));
        assertThatThrownBy(()->json.history(overflow)).isInstanceOfSatisfying(WorkflowExecutionException.class,e->assertThat(e.category()).isEqualTo(WorkflowExecutionException.Category.LIMIT_EXCEEDED));
        var em=org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class);
        var batch=new RuntimeHistoryBatch(em,json,1,2,UUID.randomUUID(),new WorkflowCommandContext(ActorKind.USER,"tester",Instant.EPOCH,null));
        for(int i=0;i<4;i++) batch.next("TEST",null,null,null,details,Map.of());
        assertThat(batch.last()).isEqualTo(4);
        org.mockito.Mockito.clearInvocations(em);
        assertThatThrownBy(()->batch.next("TEST",null,null,null,Map.of(),Map.of())).isInstanceOf(WorkflowExecutionException.class);
        org.mockito.Mockito.verifyNoInteractions(em); assertThat(batch.last()).isEqualTo(4);
    }
    @Test void historyUtf8BudgetIsIndependentOfItsUtf16Allowance() throws Exception {
        var json=new RuntimeJson();
        String raw=historyArray(4*1024*1024/3).replace("x","中");
        int remaining=4*1024*1024-raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        String exact=raw.replaceFirst("中","x".repeat(remaining)+"中");
        var node=json.tree(exact);
        assertThat(json.history(node).getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(4*1024*1024);
        var overflow=new com.fasterxml.jackson.databind.ObjectMapper().readTree(exact.replaceFirst("中","x中"));
        assertThatThrownBy(()->json.history(overflow)).isInstanceOfSatisfying(WorkflowExecutionException.class,e->assertThat(e.category()).isEqualTo(WorkflowExecutionException.Category.LIMIT_EXCEEDED));
    }
}
