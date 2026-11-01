package io.github.leontecristian.workflow.core.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.github.leontecristian.workflow.core.dto.runtime.ActionField;
import io.github.leontecristian.workflow.core.dto.runtime.BooleanLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.NullLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.ScalarValue;
import io.github.leontecristian.workflow.core.dto.runtime.StartInputs;
import io.github.leontecristian.workflow.core.dto.runtime.TextLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Binding;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowVariableEntity;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ScalarBindingsUnitTest {
    @Test void invalidCalendarAndTimestampValuesAreCallerInputErrors() {
        for(var entry:Map.of(VariableValueType.DATE,"2025-02-29",VariableValueType.DATETIME,"2026-13-01T00:00:00Z").entrySet()) {
            var field=new ActionField("value",entry.getKey(),true,false);
            var model=new MinimalWorkflowDefinition(null,null,null,List.of(),Map.of("value",new MinimalWorkflowDefinition.Input(field,null)),Map.of(),Map.of(),Map.of());
            var error=assertThrows(WorkflowExecutionException.class,() -> RuntimeScalarBindings.inputs(model,new StartInputs(Map.of("value",new TextLiteral(entry.getValue()))),new RuntimeJson()));
            assertEquals(Category.INVALID_INPUT,error.category());
        }
    }
    private ScalarValue scalar(VariableValueType type,String text) { return new ScalarValue(type,new TextLiteral(text)); }
    @ParameterizedTest
    @CsvSource({"LONG,-9223372036854775808,-9223372036854775808","LONG,9223372036854775807,9223372036854775807",
        "LONG,9007199254740993,9007199254740993","DECIMAL,12.5000,12.5","DECIMAL,0.0000000000,0",
        "DECIMAL,9999999999999999999999999999.1234567890,9999999999999999999999999999.123456789",
        "DATE,0001-01-01,0001-01-01","DATE,2024-02-29,2024-02-29",
        "DATETIME,2026-01-01T02:00:00.1234567000+02:00,2026-01-01T00:00:00.1234567Z",
        "DATETIME,2026-01-01T00:00:00.00000000000Z,2026-01-01T00:00:00Z"})
    void exactAcceptedValues(VariableValueType type,String raw,String expected) {
        var v=scalar(type,raw); assertEquals(expected,((TextLiteral)v.value()).value());
        assertEquals(v,new WorkflowVariableEntity(1,"value",v).scalar());
    }
    @ParameterizedTest
    @CsvSource({"LONG,-0","LONG,+1","LONG,01","LONG,1.0","LONG,1e1","LONG,9223372036854775808",
        "DECIMAL,-0.00","DECIMAL,.5","DECIMAL,1e2","DECIMAL,+1","DECIMAL,01.2","DECIMAL,0.00000000001",
        "DECIMAL,10000000000000000000000000000","DATE,0000-01-01","DATE,2025-02-29",
        "DATETIME,2026-01-01T00:00:00","DATETIME,2026-01-01T00:00:00.12345678Z",
        "DATETIME,2026-01-01T00:00:60Z","DATETIME,2026-01-01T00:00:00+14:01",
        "DATETIME,0001-01-01T00:00:00+14:00"})
    void inexactOrNoncanonicalEncodingsFail(VariableValueType type,String raw) { assertThrows(RuntimeException.class,() -> scalar(type,raw)); }
    @Test void tokenKindsUnicodeAndReplacementColumns() {
        assertThrows(IllegalArgumentException.class,() -> scalar(VariableValueType.BOOLEAN,"true"));
        assertThrows(IllegalArgumentException.class,() -> new ScalarValue(VariableValueType.LONG,new BooleanLiteral(true)));
        assertThrows(IllegalArgumentException.class,() -> scalar(VariableValueType.STRING,"\ud800"));
        scalar(VariableValueType.STRING,"😀".repeat(2000));
        assertThrows(IllegalArgumentException.class,() -> scalar(VariableValueType.STRING,"😀".repeat(2001)));
        var row=new WorkflowVariableEntity(1,"x",scalar(VariableValueType.LONG,"-1"));
        row.replace(scalar(VariableValueType.DECIMAL,"12.500")); assertNull(row.getLongValue()); assertNull(row.getJsonValue());
        row.replace(new ScalarValue(VariableValueType.DECIMAL,NullLiteral.INSTANCE)); assertNull(row.getDecimalValue());
        assertEquals(NullLiteral.INSTANCE,row.scalar().value());
    }
    @Test void defaultsApplyOnlyToAbsentOptionalValuesAndAbsenceRemainsAbsent() {
        var field=new ActionField("x",VariableValueType.LONG,false,true);
        var model=new MinimalWorkflowDefinition(null,null,null,List.of(),Map.of("x",new MinimalWorkflowDefinition.Input(field,new TextLiteral("12"))),Map.of(),Map.of(),Map.of());
        assertEquals(scalar(VariableValueType.LONG,"12"),RuntimeScalarBindings.inputs(model,StartInputs.empty(),new RuntimeJson()).get("x"));
        assertEquals(NullLiteral.INSTANCE,RuntimeScalarBindings.inputs(model,new StartInputs(Map.of("x",NullLiteral.INSTANCE)),new RuntimeJson()).get("x").value());
        assertThrows(WorkflowExecutionException.class,() -> RuntimeScalarBindings.inputs(model,new StartInputs(Map.of("x",new TextLiteral("bad"))),new RuntimeJson()));
        var absent=new MinimalWorkflowDefinition(null,null,null,List.of(),Map.of("x",new MinimalWorkflowDefinition.Input(field,null)),Map.of(),Map.of(),Map.of());
        assertTrue(RuntimeScalarBindings.inputs(absent,StartInputs.empty(),new RuntimeJson()).isEmpty());
    }
    @Test void swapMappingsReadOriginalSnapshotAndOptionalMissingOutputSkipsWrite() {
        var decl=Map.of("a",new ActionField("a",VariableValueType.LONG,false,false),"b",new ActionField("b",VariableValueType.LONG,false,false));
        var state=Map.of("a",scalar(VariableValueType.LONG,"1"),"b",scalar(VariableValueType.LONG,"2"));
        var writes=RuntimeScalarBindings.prepare(Map.of("a",new Binding("variable","b",null),"b",new Binding("variable","a",null)),decl,state,Map.of(),Category.INVALID_ACTION_OUTPUT);
        assertEquals(state.get("b"),writes.get("a")); assertEquals(state.get("a"),writes.get("b"));
        assertTrue(RuntimeScalarBindings.prepare(Map.of("a",new Binding("actionOutput","optional",null)),decl,state,Map.of(),Category.INVALID_ACTION_OUTPUT).isEmpty());
        assertThrows(WorkflowExecutionException.class,() -> RuntimeScalarBindings.prepare(Map.of("a",new Binding("variable","missing",null)),decl,state,Map.of(),Category.INVALID_ACTION_OUTPUT));
        assertThrows(WorkflowExecutionException.class,() -> RuntimeScalarBindings.prepare(Map.of("a",new Binding("actionOutput","o",null)),decl,state,Map.of("o",new ScalarValue(VariableValueType.LONG,NullLiteral.INSTANCE)),Category.INVALID_ACTION_OUTPUT));
        assertEquals("1",((TextLiteral)state.get("a").value()).value());
    }
    @Test void everyReturnedOutputIsValidatedEvenWhenUnmapped() {
        var fields=List.of(new ActionField("needed",VariableValueType.LONG,true,false),new ActionField("optional",VariableValueType.STRING,false,true));
        var json=new RuntimeJson();
        RuntimeScalarBindings.output(fields,Map.of("needed",scalar(VariableValueType.LONG,"2")),json);
        for(var output:List.of(Map.<String,ScalarValue>of(),Map.of("needed",scalar(VariableValueType.LONG,"2"),"unknown",scalar(VariableValueType.STRING,"x")),
                Map.of("needed",scalar(VariableValueType.STRING,"2"))))
            assertEquals(Category.INVALID_ACTION_OUTPUT,assertThrows(WorkflowExecutionException.class,() -> RuntimeScalarBindings.output(fields,output,json)).category());
    }
    @Test void completeInvocationAndStartDefaultBudgetsRejectWithoutTruncation() {
        var fields=new java.util.ArrayList<ActionField>();
        var outputs=new java.util.HashMap<String,ScalarValue>();
        var inputs=new java.util.HashMap<String,MinimalWorkflowDefinition.Input>();
        for(int i=0;i<100;i++) {
            String key="value"+i; var field=new ActionField(key,VariableValueType.STRING,false,false);
            fields.add(field); outputs.put(key,scalar(VariableValueType.STRING,"x".repeat(4000)));
            inputs.put(key,new MinimalWorkflowDefinition.Input(field,new TextLiteral("x".repeat(4000))));
        }
        assertEquals(Category.LIMIT_EXCEEDED,assertThrows(WorkflowExecutionException.class,() -> RuntimeScalarBindings.output(fields,outputs,new RuntimeJson())).category());
        var model=new MinimalWorkflowDefinition(null,null,null,List.of(),inputs,Map.of(),Map.of(),Map.of());
        assertEquals(Category.LIMIT_EXCEEDED,assertThrows(WorkflowExecutionException.class,() -> RuntimeScalarBindings.inputs(model,StartInputs.empty(),new RuntimeJson())).category());
        assertEquals(4000,((TextLiteral)outputs.get("value0").value()).value().length());
    }
}
