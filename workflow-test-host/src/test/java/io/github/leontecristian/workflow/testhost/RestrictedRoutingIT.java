package io.github.leontecristian.workflow.testhost;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leontecristian.workflow.core.dto.definition.CheckWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionCapabilities;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionManagementContext;
import io.github.leontecristian.workflow.core.dto.definition.ImportWorkflowDraft;
import io.github.leontecristian.workflow.core.dto.definition.SingleAssignmentCapability;
import io.github.leontecristian.workflow.core.dto.definition.TaskTypeCapability;
import io.github.leontecristian.workflow.core.dto.runtime.ActionField;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentContext;
import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.InstanceStatus;
import io.github.leontecristian.workflow.core.dto.runtime.ResolvedTask;
import io.github.leontecristian.workflow.core.dto.runtime.ScalarValue;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionContext;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionDescriptor;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionResult;
import io.github.leontecristian.workflow.core.dto.runtime.SingleAssignmentResult;
import io.github.leontecristian.workflow.core.dto.runtime.StartInputs;
import io.github.leontecristian.workflow.core.dto.runtime.StartWorkflow;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.TextLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowEvent;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowExecutionResult;
import io.github.leontecristian.workflow.core.dto.tasktype.CreateTaskType;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.service.definition.DefinitionCapabilityProvider;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.expression.RestrictedExpressionService;
import io.github.leontecristian.workflow.core.service.runtime.SingleTaskAssignmentResolver;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowEventRecorder;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowExecutionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowServiceAction;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import io.github.leontecristian.workflow.testhost.configuration.integration.DatabaseIntegrationTest;
import io.github.leontecristian.workflow.testhost.repository.event.ConsoleEventStore;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Real commands/publication/commits on the guarded test DB. No HTTP port or development DB. */
@Import(RestrictedRoutingIT.Adapters.class)
class RestrictedRoutingIT extends DatabaseIntegrationTest {
    @Autowired WorkflowDefinitionService definitions;
    @Autowired TaskTypeCatalogService catalog;
    @Autowired WorkflowExecutionService engine;
    @Autowired WorkflowRuntimeQueryService reads;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired TestHost host;
    @MockitoSpyBean RestrictedExpressionService expressions;
    private final ObjectMapper json=new ObjectMapper();
    private final String marker="f008.it."+UUID.randomUUID();
    private final String correlation=UUID.randomUUID().toString();
    private final String taskCode="F008_"+UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT);
    private final List<Long> instances=new ArrayList<>(), versions=new ArrayList<>();
    private Long definitionId, taskTypeId;
    private final Instant now=Instant.parse("2026-11-05T00:00:00Z");
    private TransactionTemplate tx() { var tx=new TransactionTemplate(transactions); tx.setTimeout(30); return tx; }
    private WorkflowCommandContext context(boolean complete) { return new WorkflowCommandContext(ActorKind.USER,complete?"f008-reviewer":"f008-starter",complete?now.plusSeconds(1):now,correlation); }

    @BeforeEach void createOwnedCatalog() {
        host.taskCodes.add(taskCode);
        taskTypeId=tx().execute(status->catalog.create(new CreateTaskType(taskCode,marker,null,true)).after().id());
    }
    @AfterEach void cleanOnlyOwnedFixtures() {
        host.failRecorder.set(false); host.entered=null; host.release=null;
        tx().executeWithoutResult(status->{
            for(long instance:instances) {
                jdbc.update("DELETE FROM wf_test.workflow_event_outbox WHERE instance_id=?",instance);
                jdbc.update("DELETE FROM wf.workflow_command_receipt WHERE instance_id=?",instance);
                jdbc.update("DELETE FROM wf.workflow_history WHERE instance_id=?",instance);
                jdbc.update("DELETE FROM wf.workflow_variable WHERE instance_id=?",instance);
                jdbc.update("DELETE FROM wf.workflow_task WHERE activity_id IN (SELECT id FROM wf.workflow_activity WHERE instance_id=?)",instance);
                jdbc.update("DELETE FROM wf.workflow_activity WHERE instance_id=?",instance);
                jdbc.update("DELETE FROM wf.workflow_instance WHERE id=?",instance);
            }
            jdbc.update("DELETE FROM wf_test.management_audit WHERE correlation_id=?",correlation);
            for(long version:versions) jdbc.update("DELETE FROM wf.workflow_definition_version WHERE id=?",version);
            if(definitionId!=null) jdbc.update("DELETE FROM wf.workflow_definition WHERE id=? AND workflow_key=?",definitionId,marker);
            if(taskTypeId!=null) jdbc.update("DELETE FROM wf.workflow_task_type WHERE id=? AND code=?",taskTypeId,taskCode);
        });
        host.taskCodes.remove(taskCode);
    }
    private ObjectNode fixture() throws Exception {
        return (ObjectNode)json.readTree("""
            {"schemaVersion":1,"key":"%s","version":1,"name":"Routing integration",
             "startPolicy":{"inputContract":[{"key":"quantity","type":"LONG","required":true,"nullable":false}],
               "initialize":{"quantity":{"source":"startInput","key":"quantity"},"decimal":{"source":"literal","value":"1"}}},
             "variables":[{"key":"quantity","type":"LONG","nullable":false},{"key":"recordedQuantity","type":"LONG","nullable":false},
               {"key":"missing","type":"LONG","nullable":true},{"key":"decimal","type":"DECIMAL","nullable":false}],
             "nodes":[{"key":"BEGIN","type":"START","name":"Start"},
               {"key":"REVIEW","type":"HUMAN_TASK","name":"Review","taskType":"%s","assignment":{"resolver":"f008.reviewer","inputs":{}},
                "completion":{"policy":"SINGLE"},"inputContract":[],"resultContract":{"mode":"FORBIDDEN"},
                "outcomes":[{"key":"COMPLETE","label":"Complete","requiresComment":false}]},
               {"key":"RECORD","type":"SERVICE_TASK","name":"Participating host effect","action":"f008.record",
                "inputs":{"quantity":{"source":"variable","key":"quantity"}},"outputs":{"recordedQuantity":{"source":"actionOutput","key":"recordedQuantity"}}},
               {"key":"ROUTE","type":"EXCLUSIVE_GATEWAY","name":"Choose"},
               {"key":"HIGH","type":"END","name":"High","result":"HIGH"},{"key":"LOW","type":"END","name":"Low","result":"LOW"}],
             "transitions":[{"key":"begin","from":"BEGIN","to":"REVIEW"},{"key":"complete","from":"REVIEW","to":"RECORD","outcome":"COMPLETE"},
               {"key":"record","from":"RECORD","to":"ROUTE"},{"key":"fallback","from":"ROUTE","to":"LOW","default":true},
               {"key":"high","from":"ROUTE","to":"HIGH","condition":"#vars.recordedQuantity > 10"},
               {"key":"positive","from":"ROUTE","to":"LOW","condition":"#vars.recordedQuantity > 0"}]}
            """.formatted(marker,taskCode));
    }
    private long publish(ObjectNode source) {
        return tx().execute(status->{
            var draft=definitions.importDraft(new ImportWorkflowDraft(source.toString()),new DefinitionManagementContext(marker,now)).after();
            definitionId=draft.definitionId(); versions.add(draft.id());
            var published=definitions.publish(new CheckWorkflowDefinition(draft.id(),draft.lockVersion()),new DefinitionManagementContext(marker,now));
            assertThat(published.published()).withFailMessage(()->published.report().toString()).isTrue();
            return draft.id();
        });
    }
    private WorkflowExecutionResult start(long version,String quantity) {
        var result=tx().execute(status->engine.start(new StartWorkflow(UUID.randomUUID(),version,null,null,null),
                new StartInputs(Map.of("quantity",new TextLiteral(quantity))),context(false),null)).execution();
        instances.add(result.instance().id()); return result;
    }
    private CompleteWorkflowTask command(WorkflowExecutionResult start) {
        return new CompleteWorkflowTask(UUID.randomUUID(),start.instance().id(),start.task().id(),start.instance().lockVersion(),"COMPLETE",null);
    }
    private WorkflowCommandResult complete(CompleteWorkflowTask command) { return tx().execute(status->engine.completeTask(command,context(true))); }
    private int count(String table,long instance) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE instance_id=?",Integer.class,instance); }
    private int effects() { return jdbc.queryForObject("SELECT COUNT(*) FROM wf_test.management_audit WHERE correlation_id=? AND operation='F008_EFFECT'",Integer.class,correlation); }
    private com.fasterxml.jackson.databind.JsonNode routed(long instance) throws Exception {
        return json.readTree(jdbc.queryForObject("SELECT details_json FROM wf.workflow_history WHERE instance_id=? AND event_type='GATEWAY_ROUTED'",String.class,instance));
    }
    @ParameterizedTest @ValueSource(strings={"20","5","0"})
    void orderedRoutingPersistsExactEvidenceAndReplaysWithoutEvaluation(String quantity) throws Exception {
        var started=start(publish(fixture()),quantity); var command=command(started); var completed=complete(command).execution();
        String selected=quantity.equals("20")?"high":quantity.equals("5")?"positive":"fallback";
        var details=routed(started.instance().id()); assertThat(details.path("selectedTransitionKey").asText()).isEqualTo(selected);
        assertThat(details.path("usedDefault").asBoolean()).isEqualTo(quantity.equals("0"));
        assertThat(details.path("evaluations").size()).isEqualTo(quantity.equals("20")?1:2);
        assertThat(details.path("evaluations").get(0).path("transitionKey").asText()).isEqualTo("high");
        assertThat(details.path("evaluations").get(0).path("result").asBoolean()).isEqualTo(quantity.equals("20"));
        if(!quantity.equals("20")) {
            assertThat(details.path("evaluations").get(1).path("transitionKey").asText()).isEqualTo("positive");
            assertThat(details.path("evaluations").get(1).path("result").asBoolean()).isEqualTo(quantity.equals("5"));
        }
        assertThat(details.path("evaluations").get(0).path("reads").get(0).path("value").asText()).isEqualTo(quantity);
        assertThat(completed.instance().resultKey()).isEqualTo(quantity.equals("20")?"HIGH":"LOW");
        assertThat(completed.instance().lockVersion()).isEqualTo(reads.findInstance(started.instance().id()).orElseThrow().lockVersion());
        assertThat(effects()).isEqualTo(1);
        assertThat(count("wf_test.workflow_event_outbox",started.instance().id())).isEqualTo(count("wf.workflow_history",started.instance().id()));
        var delivered=json.readTree(jdbc.queryForObject("SELECT event_json FROM wf_test.workflow_event_outbox WHERE instance_id=? AND event_type='GATEWAY_ROUTED'",String.class,started.instance().id())).path("payload");
        assertThat(delivered.size()).isEqualTo(2); assertThat(delivered.path("selectedTransitionKey").asText()).isEqualTo(selected);
        int events=count("wf.workflow_history",started.instance().id()); clearInvocations(expressions);
        assertThat(complete(command).replayed()).isTrue(); assertThat(complete(command).execution()).isEqualTo(completed);
        verify(expressions,never()).evaluate(anyString(),anyString(),anyString(),anyMap(),anyMap(),any());
        assertThat(effects()).isEqualTo(1); assertThat(count("wf.workflow_history",started.instance().id())).isEqualTo(events);
    }
    @ParameterizedTest @ValueSource(strings={"1 / 0 > 0","#vars.missing == null","false","2 ^ 1025 > 0"})
    void failuresRollbackCompletionHostEffectVariablesReceiptHistoryAndOutbox(String condition) throws Exception {
        var source=fixture(); var transitions=(ArrayNode)source.get("transitions");
        ((ObjectNode)transitions.get(4)).put("condition",condition); ((ObjectNode)transitions.get(5)).put("condition","false");
        if(condition.equals("false")) transitions.remove(3);
        var started=start(publish(source),"9223372036854775807");
        var command=command(started);
        var expected=condition.equals("false") ? WorkflowExecutionException.Category.ROUTE_NOT_FOUND
                : condition.contains("missing") ? WorkflowExecutionException.Category.UNINITIALIZED_VARIABLE
                : condition.contains("1025") ? WorkflowExecutionException.Category.EXPRESSION_LIMIT_EXCEEDED
                : WorkflowExecutionException.Category.EXPRESSION_EVALUATION_FAILED;
        assertThatThrownBy(()->complete(command)).isInstanceOfSatisfying(WorkflowExecutionException.class,e->assertThat(e.category()).isEqualTo(expected));
        assertUnchanged(started);
    }
    @ParameterizedTest @ValueSource(strings={"#vars.decimal / 2 == 0","#vars.recordedQuantity + 1 > 0","0.1 + 0.2 == 0.3","9007199254740993L == 9007199254740992.0"})
    void nativeRoundingOverflowFloatingPointAndPromotionChooseRoutesAfterSqlRoundTrip(String condition) throws Exception {
        var source=fixture();
        ((ObjectNode)source.path("transitions").get(4)).put("condition",condition);
        ((ObjectNode)source.path("transitions").get(5)).put("condition","false");
        var started=start(publish(source),"9223372036854775807");
        boolean nativeTrue=condition.startsWith("#vars.decimal") || condition.startsWith("9007");
        assertThat(complete(command(started)).execution().instance().resultKey()).isEqualTo(nativeTrue?"HIGH":"LOW");
        var evidence=routed(started.instance().id());
        assertThat(evidence.path("evaluations").get(0).path("result").asBoolean()).isEqualTo(nativeTrue);
        assertThat(effects()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT long_value FROM wf.workflow_variable WHERE instance_id=? AND variable_key='recordedQuantity'",Long.class,started.instance().id())).isEqualTo(Long.MAX_VALUE);
    }
    private void assertUnchanged(WorkflowExecutionResult started) {
        long id=started.instance().id();
        assertThat(reads.findInstance(id).orElseThrow().lockVersion()).isEqualTo(started.instance().lockVersion());
        assertThat(reads.findTask(started.task().id()).orElseThrow().status()).isEqualTo(TaskStatus.OPEN);
        assertThat(count("wf.workflow_history",id)).isEqualTo((int)started.lastHistorySequence());
        assertThat(count("wf_test.workflow_event_outbox",id)).isEqualTo((int)started.lastHistorySequence());
        assertThat(count("wf.workflow_command_receipt",id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_variable WHERE instance_id=? AND variable_key='recordedQuantity'",Integer.class,id)).isZero();
        assertThat(effects()).isZero();
    }
    @Test void recorderFailureAfterHandoffRollsBackTheSameCommand() throws Exception {
        var started=start(publish(fixture()),"20"); host.failRecorder.set(true);
        try { assertThatThrownBy(()->complete(command(started))).isInstanceOf(RuntimeException.class); }
        finally { host.failRecorder.set(false); }
        assertUnchanged(started);
    }
    @Test void gatewayBeforeHumanAndStartReplayUseTheSamePinnedContracts() throws Exception {
        var source=fixture(); var edges=(ArrayNode)source.get("transitions");
        ((ObjectNode)edges.get(0)).put("to","ROUTE"); ((ObjectNode)edges.get(2)).put("to","HIGH");
        for(int i=3;i<edges.size();i++) ((ObjectNode)edges.get(i)).put("to","REVIEW");
        ((ObjectNode)edges.get(4)).put("condition","#vars.quantity > 10"); ((ObjectNode)edges.get(5)).put("condition","#vars.quantity > 0");
        ((ArrayNode)source.get("nodes")).remove(5);
        long version=publish(source); var started=start(version,"20");
        assertThat(effects()).isZero(); assertThat(routed(started.instance().id()).path("selectedTransitionKey").asText()).isEqualTo("high");
        complete(command(started)); clearInvocations(expressions);
        var replay=tx().execute(status->engine.start(new StartWorkflow(started.commandId(),version,null,null,null),
                new StartInputs(Map.of("quantity",new TextLiteral("20"))),context(false),null));
        assertThat(replay.replayed()).isTrue(); assertThat(replay.execution()).isEqualTo(started);
        verify(expressions,never()).evaluate(anyString(),anyString(),anyString(),anyMap(),anyMap(),any());
        assertThat(effects()).isEqualTo(1);
    }
    @Test void everyScalarProjectionRoutesAfterAnActualSqlRoundTrip() throws Exception {
        var source=fixture(); var vars=(ArrayNode)source.get("variables"); var initialize=(ObjectNode)source.path("startPolicy").path("initialize");
        for(var field:Map.of("date","DATE","time","DATETIME","flag","BOOLEAN","text","STRING","optional","STRING").entrySet()) {
            vars.addObject().put("key",field.getKey()).put("type",field.getValue()).put("nullable",true);
            var binding=initialize.putObject(field.getKey()); binding.put("source","literal");
            switch(field.getKey()) {
                case "date" -> binding.put("value","2026-11-05");
                case "time" -> binding.put("value","2026-11-05T02:00:00.1234567+02:00");
                case "flag" -> binding.put("value",true); case "text" -> binding.put("value","x");
                default -> binding.putNull("value");
            }
        }
        ((ObjectNode)source.path("transitions").get(4)).put("condition","#vars.date == #vars.date and #vars.time == #vars.time and #vars.flag and #vars.text == 'x' and #vars.optional == null and #vars.decimal + #vars.quantity == #vars.quantity + 1");
        var started=start(publish(source),"20"); assertThat(complete(command(started)).execution().instance().resultKey()).isEqualTo("HIGH");
        var evidence=routed(started.instance().id()).path("evaluations").get(0).path("reads");
        assertThat(evidence.size()).isEqualTo(7);
        assertThat(evidence.get(1).path("value").asText()).isEqualTo("2026-11-05T00:00:00.1234567Z");
        assertThat(evidence.get(4).path("value").isNull()).isTrue();
    }
    @Test void oversizedCompleteRoutingEvidenceRollsBackAllEarlierParticipatingEffects() throws Exception {
        var source=fixture(); var nodes=(ArrayNode)source.get("nodes"); var vars=(ArrayNode)source.get("variables");
        var edges=(ArrayNode)source.get("transitions");
        ((ObjectNode)edges.get(2)).put("to","TEXT0"); edges.remove(5); edges.remove(4);
        for(int i=0;i<20;i++) {
            String key="text"+i, node="TEXT"+i;
            vars.addObject().put("key",key).put("type","STRING").put("nullable",false);
            var service=nodes.addObject(); service.put("key",node).put("type","SERVICE_TASK").put("name","Bounded text").put("action","f008.text");
            service.putObject("inputs"); service.putObject("outputs").putObject(key).put("source","actionOutput").put("key","text");
            edges.addObject().put("key","textEdge"+i).put("from",node).put("to",i==19?"ROUTE":"TEXT"+(i+1));
            edges.addObject().put("key","check"+i).put("from","ROUTE").put("to","HIGH").put("condition","#vars."+key+" == ''");
        }
        var started=start(publish(source),"20");
        assertThatThrownBy(()->complete(command(started))).isInstanceOfSatisfying(WorkflowExecutionException.class,e->assertThat(e.category()).isEqualTo(WorkflowExecutionException.Category.LIMIT_EXCEEDED));
        assertUnchanged(started);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_variable WHERE instance_id=? AND variable_key LIKE 'text%'",Integer.class,started.instance().id())).isZero();
    }
    @ParameterizedTest @ValueSource(ints={48,49})
    void gatewayVisitsAndEndShareTheFiftyAutomaticSteps(int gateways) throws Exception {
        var source=fixture(); var nodes=(ArrayNode)source.get("nodes"); nodes.remove(5); nodes.remove(3);
        var edges=(ArrayNode)source.get("transitions"); while(edges.size()>3) edges.remove(3);
        ((ObjectNode)edges.get(2)).put("to","GW0");
        for(int i=0;i<gateways;i++) {
            nodes.addObject().put("key","GW"+i).put("type","EXCLUSIVE_GATEWAY").put("name","Bounded step");
            edges.addObject().put("key","gw"+i).put("from","GW"+i).put("to",i==gateways-1?"HIGH":"GW"+(i+1)).put("default",true);
        }
        var started=start(publish(source),"20");
        if(gateways==48) {
            assertThat(complete(command(started)).execution().instance().resultKey()).isEqualTo("HIGH"); assertThat(effects()).isEqualTo(1);
        } else {
            assertThatThrownBy(()->complete(command(started))).isInstanceOfSatisfying(WorkflowExecutionException.class,e->assertThat(e.category()).isEqualTo(WorkflowExecutionException.Category.LIMIT_EXCEEDED));
            assertUnchanged(started);
        }
    }
    @ParameterizedTest @ValueSource(ints={49,50})
    void startMayPauseAtHumanAfterStepFiftyButNeverExecutesFiftyOne(int gateways) throws Exception {
        var source=fixture(); var nodes=(ArrayNode)source.get("nodes"); nodes.remove(5); nodes.remove(3);
        var edges=(ArrayNode)source.get("transitions"); while(edges.size()>3) edges.remove(3);
        ((ObjectNode)edges.get(0)).put("to","GW0"); ((ObjectNode)edges.get(2)).put("to","HIGH");
        for(int i=0;i<gateways;i++) {
            nodes.addObject().put("key","GW"+i).put("type","EXCLUSIVE_GATEWAY").put("name","Bounded start step");
            edges.addObject().put("key","gw"+i).put("from","GW"+i).put("to",i==gateways-1?"REVIEW":"GW"+(i+1)).put("default",true);
        }
        long version=publish(source);
        if(gateways==49) assertThat(start(version,"20").instance().status()).isEqualTo(InstanceStatus.ACTIVE);
        else {
            assertThatThrownBy(()->start(version,"20")).isInstanceOfSatisfying(WorkflowExecutionException.class,e->assertThat(e.category()).isEqualTo(WorkflowExecutionException.Category.LIMIT_EXCEEDED));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_instance WHERE definition_version_id=?",Integer.class,version)).isZero();
        }
    }
    @Test void reorderedNewVersionDoesNotChangeAnExistingPin() throws Exception {
        var source=fixture(); var old=start(publish(source),"20");
        source.put("version",2); var edges=(ArrayNode)source.get("transitions"); var high=edges.remove(4); edges.add(high);
        var fresh=start(publish(source),"20");
        assertThat(complete(command(old)).execution().instance().resultKey()).isEqualTo("HIGH");
        assertThat(complete(command(fresh)).execution().instance().resultKey()).isEqualTo("LOW");
        assertThat(routed(old.instance().id()).path("selectedTransitionKey").asText()).isEqualTo("high");
        assertThat(routed(fresh.instance().id()).path("selectedTransitionKey").asText()).isEqualTo("positive");
    }
    @Test void overlappingCompletionsCommitExactlyOneEffectAndRoute() throws Exception {
        var started=start(publish(fixture()),"20");
        host.entered=new CountDownLatch(1); host.release=new CountDownLatch(1);
        var executor=Executors.newFixedThreadPool(2);
        try {
            var first=executor.submit(()->complete(command(started)));
            assertThat(host.entered.await(10,TimeUnit.SECONDS)).isTrue();
            var secondStarted=new CountDownLatch(1);
            var second=executor.submit(()->tx().execute(status->{ secondStarted.countDown(); return engine.completeTask(command(started),context(true)); }));
            assertThat(secondStarted.await(10,TimeUnit.SECONDS)).isTrue(); host.release.countDown();
            assertThat(first.get(20,TimeUnit.SECONDS).replayed()).isFalse();
            assertThatThrownBy(()->second.get(20,TimeUnit.SECONDS)).hasCauseInstanceOf(WorkflowExecutionException.class);
            assertThat(effects()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_history WHERE instance_id=? AND event_type='GATEWAY_ROUTED'",Integer.class,started.instance().id())).isEqualTo(1);
        } finally { host.release.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); }
    }
    @TestConfiguration static class Adapters {
        @Bean TestHost routingTestHost(JdbcTemplate jdbc,ConsoleEventStore outbox,ConsoleWire wire) { return new TestHost(jdbc,outbox,wire); }
        @Bean WorkflowServiceAction routingTextAction(TestHost host) {
            return new WorkflowServiceAction() {
                @Override public ServiceActionDescriptor descriptor() { return new ServiceActionDescriptor("f008.text",List.of(),List.of(new ActionField("text",VariableValueType.STRING,true,false))); }
                @Override public ServiceActionResult execute(ServiceActionContext context,Map<String,ScalarValue> inputs) {
                    host.effect(context);
                    return new ServiceActionResult(Map.of("text",new ScalarValue(VariableValueType.STRING,new TextLiteral("x".repeat(4000)))));
                }
            };
        }
    }
    static class TestHost implements DefinitionCapabilityProvider,SingleTaskAssignmentResolver,WorkflowServiceAction,WorkflowEventRecorder {
        final Set<String> taskCodes=ConcurrentHashMap.newKeySet(); final AtomicBoolean failRecorder=new AtomicBoolean();
        volatile CountDownLatch entered,release;
        private final JdbcTemplate jdbc; private final ConsoleEventStore outbox; private final ConsoleWire wire;
        TestHost(JdbcTemplate jdbc,ConsoleEventStore outbox,ConsoleWire wire) { this.jdbc=jdbc; this.outbox=outbox; this.wire=wire; }
        @Override public DefinitionCapabilities snapshot() {
            var types=new HashSet<TaskTypeCapability>(); taskCodes.forEach(code->types.add(new TaskTypeCapability(code,Set.of("COMPLETE"))));
            return new DefinitionCapabilities(Set.of(new SingleAssignmentCapability(identifier())),types);
        }
        @Override public String identifier() { return "f008.reviewer"; }
        @Override public SingleAssignmentResult resolve(AssignmentContext context) { return new SingleAssignmentResult(List.of(new ResolvedTask("review","f008-reviewer")),null); }
        @Override public ServiceActionDescriptor descriptor() { return new ServiceActionDescriptor("f008.record",
                List.of(new ActionField("quantity",VariableValueType.LONG,true,false)),List.of(new ActionField("recordedQuantity",VariableValueType.LONG,true,false))); }
        @Override public ServiceActionResult execute(ServiceActionContext context,Map<String,ScalarValue> inputs) {
            effect(context);
            var began=entered; var finish=release;
            if(began!=null && finish!=null) {
                began.countDown();
                try { if(!finish.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Test release timeout"); }
                catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }
            return new ServiceActionResult(Map.of("recordedQuantity",inputs.get("quantity")));
        }
        void effect(ServiceActionContext context) {
            jdbc.update("INSERT INTO wf_test.management_audit(occurred_at,actor_ref,operation,entity_kind,entity_id,correlation_id,evidence_json) VALUES(SYSUTCDATETIME(),?,'F008_EFFECT','TASK_TYPE',1,?,N'{}')",
                    context.commandContext().actorRef(),context.commandContext().correlationId());
        }
        @Override public void record(List<WorkflowEvent> events) {
            for(var event:events) outbox.record(event,wire.json(event));
            if(failRecorder.get()) throw new IllegalStateException("Test recorder rollback");
        }
    }
}
