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
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue;
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
                "completion":{"policy":"SINGLE"},"resultContract":{"mode":"FORBIDDEN"},
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
        assertThat(evidence.size()).isEqualTo(10);
        assertThat(evidence.get(2).path("value").asText()).isEqualTo("2026-11-05T00:00:00.1234567Z");
        assertThat(evidence.get(6).path("value").isNull()).isTrue();
    }
    @Test void scalarRoutingEvidenceFitsTheNewHistoryCapacityWithoutDeduplicatingReads() throws Exception {
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
        assertThat(complete(command(started)).execution().instance().resultKey()).isEqualTo("LOW");
        assertThat(routed(started.instance().id()).path("evaluations")).hasSize(20);
        assertThat(effects()).isEqualTo(21);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_variable WHERE instance_id=? AND variable_key LIKE 'text%'",Integer.class,started.instance().id())).isEqualTo(20);
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
    @ParameterizedTest @ValueSource(booleans={false,true})
    void overlappingCompletionsCommitExactlyOneEffectAndRoute(boolean jsonValues) throws Exception {
        var started=jsonValues ? jsonStart(publish(jsonFixture("#vars.booking.amount > 0")),UUID.randomUUID(),"{\"amount\":1.00,\"obsolete\":true}") : start(publish(fixture()),"20");
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
    private ObjectNode jsonFixture(String condition) throws Exception {
        var source=fixture(); var policy=(ObjectNode)source.get("startPolicy");
        policy.putArray("inputContract").addObject().put("key","booking").put("type","JSON").put("required",true).put("nullable",false);
        var initialize=policy.putObject("initialize"); initialize.putObject("booking").put("source","startInput").put("key","booking");
        initialize.putObject("explicitNull").put("source","literal").putNull("value");
        var vars=source.putArray("variables");
        vars.addObject().put("key","booking").put("type","JSON").put("nullable",false);
        vars.addObject().put("key","explicitNull").put("type","JSON").put("nullable",true);
        vars.addObject().put("key","uninitialized").put("type","JSON").put("nullable",true);
        var node=(ObjectNode)source.path("nodes").get(2); node.put("action","f009.replace");
        node.putObject("inputs").putObject("booking").put("source","variable").put("key","booking");
        node.putObject("outputs").putObject("booking").put("source","actionOutput").put("key","booking");
        ((ObjectNode)source.path("transitions").get(4)).put("condition",condition);
        ((ObjectNode)source.path("transitions").get(5)).put("condition","false");
        return source;
    }
    private WorkflowExecutionResult jsonStart(long version,UUID command,String value) {
        var started=tx().execute(status->engine.start(new StartWorkflow(command,version,null,null,null),
                new StartInputs(Map.of("booking",WorkflowJsonValue.parse(value))),context(false),null)).execution();
        instances.add(started.instance().id()); return started;
    }
    @Test void jsonSnapshotsRoundTripReplaceRouteAndReplayWithCoordinatedHistoryReaders() throws Exception {
        String original="{\"amount\":12500.00,\"department\":{\"code\":\"SALES\"},\"obsolete\":true}";
        long version=publish(jsonFixture("#vars.booking.amount > 10000 and #vars.booking.department?.code == 'SALES'"));
        UUID command=UUID.randomUUID(); var started=jsonStart(version,command,original);
        var page=reads.listVariables(started.instance().id(),null,100,null);
        assertThat(page.items()).filteredOn(v->v.key().equals("explicitNull")).allSatisfy(v->{ assertThat(v.initialized()).isTrue(); assertThat(v.value().value()).isEqualTo(io.github.leontecristian.workflow.core.dto.runtime.NullLiteral.INSTANCE); });
        assertThat(page.items()).filteredOn(v->v.key().equals("uninitialized")).allSatisfy(v->assertThat(v.initialized()).isFalse());
        assertThat(jdbc.queryForObject("SELECT json_value FROM wf.workflow_variable WHERE instance_id=? AND variable_key='booking'",String.class,started.instance().id())).contains("12500.00");
        var closed=complete(command(started)).execution(); assertThat(closed.instance().resultKey()).isEqualTo("HIGH");
        assertThat(reads.findTaskSubmission(started.task().id())).isPresent();
        assertThat(closed.instance().lockVersion()).isEqualTo(reads.findInstance(started.instance().id()).orElseThrow().lockVersion());
        assertThat(jdbc.queryForObject("SELECT json_value FROM wf.workflow_variable WHERE instance_id=? AND variable_key='booking'",String.class,started.instance().id())).doesNotContain("obsolete").contains("12500.00");
        var evidence=routed(started.instance().id()).path("evaluations").get(0).path("reads");
        assertThat(evidence).hasSize(2); assertThat(evidence.get(0).path("path").get(0).path("key").asText()).isEqualTo("amount");
        assertThat(evidence.get(0).path("type").asText()).isEqualTo("JSON");
        String routingText=jdbc.queryForObject("SELECT details_json FROM wf.workflow_history WHERE instance_id=? AND event_type='GATEWAY_ROUTED'",String.class,started.instance().id());
        assertThat(routingText).contains("12500.00").doesNotContain("obsolete");
        String writes=jdbc.queryForObject("SELECT details_json FROM wf.workflow_history WHERE instance_id=? AND event_type='VARIABLES_WRITTEN' AND details_json LIKE '%actionOutput%'",String.class,started.instance().id());
        assertThat(writes).contains("previousValue","obsolete","12500.00");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_history WHERE instance_id=? AND details_schema_version<>2",Integer.class,started.instance().id())).isZero();
        clearInvocations(expressions);
        var replay=tx().execute(status->engine.start(new StartWorkflow(command,version,null,null,null),new StartInputs(Map.of("booking",WorkflowJsonValue.parse("{\"obsolete\":true,\"department\":{\"code\":\"SALES\"},\"amount\":12500.00}"))),context(false),null));
        assertThat(replay.replayed()).isTrue(); assertThat(replay.execution()).isEqualTo(started); assertThat(effects()).isEqualTo(1);
        verify(expressions,never()).evaluate(anyString(),anyString(),anyString(),anyMap(),anyMap(),any());
        assertThatThrownBy(()->tx().execute(status->engine.start(new StartWorkflow(command,version,null,null,null),new StartInputs(Map.of("booking",WorkflowJsonValue.parse(original.replace("12500.00","12500.0")))),context(false),null)))
                .isInstanceOfSatisfying(WorkflowExecutionException.class,e->assertThat(e.category()).isEqualTo(WorkflowExecutionException.Category.COMMAND_ID_CONFLICT));
        var constraint=jdbc.queryForMap("SELECT definition,is_disabled,is_not_trusted FROM sys.check_constraints WHERE name='ck_workflow_history_json'");
        assertThat(constraint.get("definition").toString()).contains("4194304","isjson"); assertThat(constraint.get("is_disabled")).isEqualTo(false); assertThat(constraint.get("is_not_trusted")).isEqualTo(false);
    }
    @Test void jsonFailureAfterReplacementRollsBackHostEffectValuesAndHistory() throws Exception {
        var started=jsonStart(publish(jsonFixture("#vars.booking.missing.code == 'x'")),UUID.randomUUID(),"{\"amount\":1.00,\"obsolete\":true}");
        assertThatThrownBy(()->complete(command(started))).isInstanceOfSatisfying(WorkflowExecutionException.class,e->assertThat(e.category()).isEqualTo(WorkflowExecutionException.Category.EXPRESSION_EVALUATION_FAILED));
        assertThat(jdbc.queryForObject("SELECT json_value FROM wf.workflow_variable WHERE instance_id=? AND variable_key='booking'",String.class,started.instance().id())).contains("obsolete","1.00");
        assertThat(reads.findTask(started.task().id()).orElseThrow().status()).isEqualTo(TaskStatus.OPEN);
        assertThat(count("wf.workflow_history",started.instance().id())).isEqualTo((int)started.lastHistorySequence());
        assertThat(count("wf.workflow_command_receipt",started.instance().id())).isEqualTo(1); assertThat(effects()).isZero();
    }
    private String maximumJson(String character) {
        var out=new StringBuilder("["); int remaining=128*1024-28;
        for(int i=0;i<9;i++) { if(i>0) out.append(','); int n=Math.min(16000,remaining); out.append('"').append(character.repeat(n)).append('"'); remaining-=n; }
        return out.append(']').toString();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void maximumJsonStartDefaultServiceAndReadRoundTrip(boolean useDefault) throws Exception {
        String maximum=maximumJson("x");
        var source=jsonFixture("#vars.explicitNull == null");
        if(useDefault) {
            var input=(ObjectNode)source.path("startPolicy").path("inputContract").get(0); input.put("required",false);
            input.set("default",json.readTree(maximum));
        }
        long version=publish(source);
        WorkflowExecutionResult started;
        if(useDefault) {
            started=tx().execute(status->engine.start(new StartWorkflow(UUID.randomUUID(),version,null,null,null),StartInputs.empty(),context(false),null)).execution();
            instances.add(started.instance().id());
        } else started=jsonStart(version,UUID.randomUUID(),maximum);
        assertThat(jdbc.queryForObject("SELECT DATALENGTH(json_value) FROM wf.workflow_variable WHERE instance_id=? AND variable_key='booking'",Integer.class,started.instance().id())).isEqualTo(256*1024);
        complete(command(started));
        var page=reads.listVariables(started.instance().id(),null,100,null);
        assertThat(page.items()).filteredOn(v->v.key().equals("booking")).allSatisfy(v->assertThat(((WorkflowJsonValue)v.value().value()).toJson()).isEqualTo(maximum));
        var sequences=jdbc.queryForList("SELECT sequence_number FROM wf.workflow_history WHERE instance_id=? AND event_type='VARIABLES_WRITTEN'",Long.class,started.instance().id());
        assertThat(sequences).hasSize(2);
        for(long sequence:sequences) assertThat(reads.findHistoryEvent(started.instance().id(),sequence).orElseThrow().detailsJson()).contains(maximum);
    }
    @Test void unicodeVariablePagesFitActualUtf8AndKeepStableContinuation() throws Exception {
        String maximum=maximumJson("中"); var source=jsonFixture("#vars.explicitNull == null");
        for(String key:List.of("bcopy","ccopy")) {
            ((ArrayNode)source.get("variables")).addObject().put("key",key).put("type","JSON").put("nullable",false);
            ((ObjectNode)source.path("startPolicy").path("initialize")).putObject(key).put("source","startInput").put("key","booking");
        }
        var started=jsonStart(publish(source),UUID.randomUUID(),maximum);
        var page=reads.listVariables(started.instance().id(),null,100,null);
        assertThat(page.items()).hasSize(2); assertThat(page.nextAfterKey()).isEqualTo("booking");
        var next=reads.listVariables(started.instance().id(),page.nextAfterKey(),100,page.lockVersion());
        assertThat(next.items()).extracting(v->v.key()).containsExactly("ccopy","explicitNull","uninitialized");
        assertThat(next.nextAfterKey()).isNull();
        assertThatThrownBy(()->reads.listVariables(started.instance().id(),page.nextAfterKey(),100,null)).isInstanceOf(WorkflowExecutionException.class);
    }
    @Test void cumulativeJsonEvidenceOverflowRollsBackEarlierReplacementEffectsAndClaim() throws Exception {
        var source=jsonFixture("#vars.explicitNull == null");
        var nodes=(ArrayNode)source.get("nodes"); var edges=(ArrayNode)source.get("transitions");
        ((ObjectNode)edges.get(2)).put("to","COPY0");
        for(int i=0;i<17;i++) {
            var node=nodes.addObject().put("key","COPY"+i).put("type","SERVICE_TASK").put("name","Retained snapshot").put("action","f009.replace");
            node.putObject("inputs").putObject("booking").put("source","variable").put("key","booking");
            node.putObject("outputs").putObject("booking").put("source","actionOutput").put("key","booking");
            edges.addObject().put("key","copyEdge"+i).put("from","COPY"+i).put("to",i==16 ? "ROUTE" : "COPY"+(i+1));
        }
        var started=jsonStart(publish(source),UUID.randomUUID(),maximumJson("x"));
        assertThatThrownBy(()->complete(command(started))).isInstanceOfSatisfying(WorkflowExecutionException.class,e->assertThat(e.category()).isEqualTo(WorkflowExecutionException.Category.LIMIT_EXCEEDED));
        assertThat(effects()).isZero();
        assertThat(reads.findInstance(started.instance().id()).orElseThrow().lockVersion()).isEqualTo(started.instance().lockVersion());
        assertThat(reads.findTask(started.task().id()).orElseThrow().status()).isEqualTo(TaskStatus.OPEN);
        assertThat(count("wf.workflow_history",started.instance().id())).isEqualTo((int)started.lastHistorySequence());
        assertThat(count("wf_test.workflow_event_outbox",started.instance().id())).isEqualTo((int)started.lastHistorySequence());
    }
    @Test void routingRecordOverflowRollsBackReplacementAndHostEffect() throws Exception {
        var source=jsonFixture("#vars.booking == null");
        var edges=(ArrayNode)source.get("transitions");
        for(int i=0;i<17;i++) edges.addObject().put("key","largeRead"+i).put("from","ROUTE").put("to","HIGH").put("condition","#vars.booking == null");
        var started=jsonStart(publish(source),UUID.randomUUID(),maximumJson("x"));
        assertThatThrownBy(()->complete(command(started))).isInstanceOfSatisfying(WorkflowExecutionException.class,e->assertThat(e.category()).isEqualTo(WorkflowExecutionException.Category.LIMIT_EXCEEDED));
        assertThat(effects()).isZero(); assertThat(reads.findTask(started.task().id()).orElseThrow().status()).isEqualTo(TaskStatus.OPEN);
        assertThat(count("wf.workflow_history",started.instance().id())).isEqualTo((int)started.lastHistorySequence());
        assertThat(count("wf_test.workflow_event_outbox",started.instance().id())).isEqualTo((int)started.lastHistorySequence());
    }
    /** F010: the minimal outcome branch must not depend on a gateway or variables. */
    private ObjectNode outcomeFixture(boolean jsonValues, boolean failAfterEffect) throws Exception {
        var source = jsonValues ? jsonFixture(failAfterEffect ? "1 / 0 > 0" : "#vars.booking.amount > 0") : fixture();
        var human = (ObjectNode) source.path("nodes").get(1);
        var outcomes = human.putArray("outcomes");
        outcomes.addObject().put("key", "APPROVE").put("label", "Approve").put("requiresComment", false);
        outcomes.addObject().put("key", "REJECT").put("label", "Reject").put("requiresComment", true);
        var edges = (ArrayNode) source.get("transitions");
        ((ObjectNode) edges.get(1)).put("outcome", "APPROVE").put("key", "approve");
        if (jsonValues) {
            // Both branches have a distinct service node; only one may run.
            var record = (ObjectNode) source.path("nodes").get(2);
            ((ArrayNode) source.get("nodes")).add(record.deepCopy().put("key", "REJECT_RECORD"));
            edges.addObject().put("key", "reject").put("from", "REVIEW").put("to", "REJECT_RECORD").put("outcome", "REJECT");
            edges.addObject().put("key", "rejectedEnd").put("from", "REJECT_RECORD").put("to", "LOW");
        } else {
            source.putArray("variables");
            var policy = source.putObject("startPolicy"); policy.putArray("inputContract");
            var nodes = (ArrayNode) source.get("nodes"); nodes.remove(3); nodes.remove(2);
            while (edges.size() > 2) edges.remove(2);
            ((ObjectNode) edges.get(1)).put("to", "HIGH");
            edges.addObject().put("key", "reject").put("from", "REVIEW").put("to", "LOW").put("outcome", "REJECT");
        }
        return source;
    }
    private WorkflowExecutionResult outcomeStart(ObjectNode source, boolean jsonValues) {
        long version = publish(source);
        if (jsonValues) return jsonStart(version, UUID.randomUUID(), "{\"amount\":1.00,\"obsolete\":true}");
        var result = tx().execute(status -> engine.start(new StartWorkflow(UUID.randomUUID(), version, null, null, null), context(false), null)).execution();
        instances.add(result.instance().id()); return result;
    }
    private CompleteWorkflowTask outcomeCommand(WorkflowExecutionResult started, String outcome, String comment) {
        return new CompleteWorkflowTask(UUID.randomUUID(), started.instance().id(), started.task().id(), started.instance().lockVersion(), outcome, comment);
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void f010ExactOutcomeRoutesEvidenceSubmissionAndReplay(boolean jsonValues) throws Exception {
        var source = outcomeFixture(jsonValues, false);
        for (String outcome : List.of("APPROVE", "REJECT")) {
            if (outcome.equals("REJECT")) {
                // New immutable version reverses presentation and edge order.
                source.put("version", 2);
                var choices = (ArrayNode) source.path("nodes").get(1).path("outcomes"); choices.add(choices.remove(0));
                var edges = (ArrayNode) source.get("transitions"); edges.add(edges.remove(1));
            }
            var started = outcomeStart(source, jsonValues);
            String comment = outcome.equals("REJECT") ? "  reason\n" : null;
            var command = outcomeCommand(started, outcome, comment);
            var completed = complete(command).execution();
            assertThat(completed.instance().status()).isEqualTo(InstanceStatus.COMPLETED);
            assertThat(completed.instance().resultKey()).isEqualTo(outcome.equals("APPROVE") ? "HIGH" : "LOW");
            assertThat(completed.task().outcomeKey()).isEqualTo(outcome);
            assertThat(completed.activity().outcomeKey()).isEqualTo(outcome);
            assertThat(completed.instance().lockVersion()).isEqualTo(reads.findInstance(started.instance().id()).orElseThrow().lockVersion());
            var facts = jdbc.queryForList("SELECT event_type,details_json FROM wf.workflow_history WHERE instance_id=? AND event_type IN ('TASK_COMPLETED','ACTIVITY_COMPLETED','TRANSITION_TAKEN') ORDER BY sequence_number", started.instance().id());
            int outcomeFacts = 0;
            for (var fact : facts) {
                var detail = json.readTree((String) fact.get("details_json"));
                if (detail.has("outcomeKey")) { assertThat(detail.path("outcomeKey").asText()).isEqualTo(outcome); outcomeFacts++; }
            }
            assertThat(outcomeFacts).isEqualTo(3);
            assertThat(jdbc.queryForList("SELECT transition_key FROM wf.workflow_history WHERE instance_id=? AND source_node_key='REVIEW'", String.class, started.instance().id()))
                    .containsExactly(outcome.toLowerCase(Locale.ROOT));
            assertThat(reads.findTaskSubmission(started.task().id()).orElseThrow().outcomeKey()).isEqualTo(outcome);
            int effectsBeforeReplay = effects();
            assertThat(complete(command).execution()).isEqualTo(completed);
            assertThat(complete(command).replayed()).isTrue();
            assertThat(effects()).isEqualTo(effectsBeforeReplay);
            var changed = new CompleteWorkflowTask(command.commandId(), command.instanceId(), command.taskId(), command.expectedLockVersion(), outcome.equals("APPROVE") ? "REJECT" : "APPROVE", comment);
            assertThatThrownBy(() -> complete(changed)).isInstanceOfSatisfying(WorkflowExecutionException.class, e -> assertThat(e.category()).isEqualTo(WorkflowExecutionException.Category.COMMAND_ID_CONFLICT));
            var changedComment = new CompleteWorkflowTask(command.commandId(), command.instanceId(), command.taskId(), command.expectedLockVersion(), outcome, "different");
            assertThatThrownBy(() -> complete(changedComment)).isInstanceOf(WorkflowExecutionException.class);
            if (jsonValues) {
                var executed = jdbc.queryForList("SELECT source_node_key FROM wf.workflow_history WHERE instance_id=? AND event_type='SERVICE_TASK_COMPLETED'", String.class, started.instance().id());
                // Service facts retain nodeKey in details, rather than transition source metadata.
                assertThat(executed).hasSize(1);
                var details = json.readTree(jdbc.queryForObject("SELECT details_json FROM wf.workflow_history WHERE instance_id=? AND event_type='SERVICE_TASK_COMPLETED'", String.class, started.instance().id()));
                assertThat(details.path("nodeKey").asText()).isEqualTo(outcome.equals("APPROVE") ? "RECORD" : "REJECT_RECORD");
            }
        }
        assertThat(effects()).isEqualTo(jsonValues ? 2 : 0);
    }
    @ParameterizedTest @ValueSource(strings = {"", " ", "\n\t", "UNKNOWN", "approve"})
    void f010InvalidOutcomesAndRequiredCommentsLeaveNoCommittedChanges(String value) throws Exception {
        var started = outcomeStart(outcomeFixture(false, false), false);
        var command = value.isBlank() ? outcomeCommand(started, "REJECT", value) : outcomeCommand(started, value, null);
        assertThatThrownBy(() -> complete(command)).isInstanceOfSatisfying(WorkflowExecutionException.class, e -> assertThat(e.category()).isEqualTo(WorkflowExecutionException.Category.INVALID_INPUT));
        assertUnchanged(started);
    }
    @Test void f010SelectedJsonBranchFailureRollsBackAllParticipatingChanges() throws Exception {
        var started = outcomeStart(outcomeFixture(true, true), true);
        assertThatThrownBy(() -> complete(outcomeCommand(started, "APPROVE", null))).isInstanceOfSatisfying(WorkflowExecutionException.class, e -> assertThat(e.category()).isEqualTo(WorkflowExecutionException.Category.EXPRESSION_EVALUATION_FAILED));
        assertThat(effects()).isZero();
        assertThat(reads.findTask(started.task().id()).orElseThrow().status()).isEqualTo(TaskStatus.OPEN);
        assertThat(reads.findInstance(started.instance().id()).orElseThrow().lockVersion()).isEqualTo(started.instance().lockVersion());
        assertThat(count("wf.workflow_history", started.instance().id())).isEqualTo((int) started.lastHistorySequence());
        assertThat(count("wf_test.workflow_event_outbox", started.instance().id())).isEqualTo((int) started.lastHistorySequence());
        assertThat(count("wf.workflow_command_receipt", started.instance().id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT json_value FROM wf.workflow_variable WHERE instance_id=? AND variable_key='booking'", String.class, started.instance().id())).contains("\"obsolete\":true");
    }
    @Test void f010CompetingOutcomesCommitOnlyTheWinner() throws Exception {
        var started = outcomeStart(outcomeFixture(true, false), true);
        host.entered = new CountDownLatch(1); host.release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> complete(outcomeCommand(started, "APPROVE", null)));
            assertThat(host.entered.await(10, TimeUnit.SECONDS)).isTrue();
            var secondStarted = new CountDownLatch(1);
            var second = executor.submit(() -> tx().execute(status -> { secondStarted.countDown(); return engine.completeTask(outcomeCommand(started, "REJECT", "reason"), context(true)); }));
            assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue(); host.release.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS).execution().task().outcomeKey()).isEqualTo("APPROVE");
            assertThatThrownBy(() -> second.get(20, TimeUnit.SECONDS)).hasCauseInstanceOf(WorkflowExecutionException.class);
            assertThat(effects()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT outcome_key FROM wf.workflow_task WHERE id=?", String.class, started.task().id())).isEqualTo("APPROVE");
        } finally { host.release.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }
    @Test void f010PinnedOutcomesSurviveCatalogDisablingAndWrongAssigneeCannotComplete() throws Exception {
        var source = outcomeFixture(false, false);
        long version = publish(source);
        tx().executeWithoutResult(status -> catalog.update(new io.github.leontecristian.workflow.core.dto.tasktype.UpdateTaskType(taskTypeId, 0, marker, null, false)));
        var started = tx().execute(status -> engine.start(new StartWorkflow(UUID.randomUUID(), version, null, null, null), context(false), null)).execution();
        instances.add(started.instance().id());
        var command = outcomeCommand(started, "REJECT", null);
        assertThatThrownBy(() -> complete(command)).isInstanceOfSatisfying(WorkflowExecutionException.class,
                e -> assertThat(e.category()).isEqualTo(WorkflowExecutionException.Category.INVALID_INPUT));
        var wrongActor = new WorkflowCommandContext(ActorKind.USER, "other-reviewer", now.plusSeconds(1), correlation);
        var valid = outcomeCommand(started, "REJECT", "reason");
        assertThatThrownBy(() -> tx().execute(status -> engine.completeTask(valid, wrongActor))).isInstanceOfSatisfying(WorkflowExecutionException.class,
                e -> assertThat(e.category()).isEqualTo(WorkflowExecutionException.Category.NOT_ASSIGNED));
        assertUnchanged(started);
        assertThat(complete(valid).execution().instance().resultKey()).isEqualTo("LOW");
        assertThatThrownBy(() -> tx().execute(status -> definitions.replaceDraft(new io.github.leontecristian.workflow.core.dto.definition.ReplaceWorkflowDraft(
                version, definitions.findVersion(version).orElseThrow().summary().lockVersion(), source.toString()), new DefinitionManagementContext(marker, now))))
                .isInstanceOf(io.github.leontecristian.workflow.core.exception.definition.WorkflowDefinitionException.class);
    }

    @TestConfiguration static class Adapters {
        @Bean WorkflowServiceAction jsonReplacementAction(TestHost host) {
            return new WorkflowServiceAction() {
                public ServiceActionDescriptor descriptor() { var f=new ActionField("booking",VariableValueType.JSON,true,false); return new ServiceActionDescriptor("f009.replace",List.of(f),List.of(f)); }
                public ServiceActionResult execute(ServiceActionContext context,Map<String,WorkflowValue> inputs) {
                    host.effect(context); host.awaitRelease(); var input=(WorkflowJsonValue)inputs.get("booking").value();
                    var output=WorkflowJsonValue.parse(input.toJson().replace(",\"obsolete\":true",""));
                    return new ServiceActionResult(Map.of("booking",new WorkflowValue(VariableValueType.JSON,output)));
                }
            };
        }
        @Bean TestHost routingTestHost(JdbcTemplate jdbc,ConsoleEventStore outbox,ConsoleWire wire) { return new TestHost(jdbc,outbox,wire); }
        @Bean WorkflowServiceAction routingTextAction(TestHost host) {
            return new WorkflowServiceAction() {
                @Override public ServiceActionDescriptor descriptor() { return new ServiceActionDescriptor("f008.text",List.of(),List.of(new ActionField("text",VariableValueType.STRING,true,false))); }
                @Override public ServiceActionResult execute(ServiceActionContext context,Map<String,WorkflowValue> inputs) {
                    host.effect(context);
                    return new ServiceActionResult(Map.of("text",new WorkflowValue(VariableValueType.STRING,new TextLiteral("x".repeat(4000)))));
                }
            };
        }
    }
    static class TestHost implements DefinitionCapabilityProvider,SingleTaskAssignmentResolver,WorkflowServiceAction,WorkflowEventRecorder {
        @Override public Set<io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind> targetKinds() { return Set.of(io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind.USER); }
        final Set<String> taskCodes=ConcurrentHashMap.newKeySet(); final AtomicBoolean failRecorder=new AtomicBoolean();
        volatile CountDownLatch entered,release;
        private final JdbcTemplate jdbc; private final ConsoleEventStore outbox; private final ConsoleWire wire;
        TestHost(JdbcTemplate jdbc,ConsoleEventStore outbox,ConsoleWire wire) { this.jdbc=jdbc; this.outbox=outbox; this.wire=wire; }
        @Override public DefinitionCapabilities snapshot() {
            var types=new HashSet<TaskTypeCapability>(); taskCodes.forEach(code->types.add(new TaskTypeCapability(code,Set.of("COMPLETE", "APPROVE", "REJECT"))));
            return new DefinitionCapabilities(Set.of(new SingleAssignmentCapability(identifier(), Set.of(io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind.USER))),types);
        }
        @Override public String identifier() { return "f008.reviewer"; }
        @Override public SingleAssignmentResult resolve(AssignmentContext context) { return new SingleAssignmentResult(List.of(new ResolvedTask("review", new io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget(io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind.USER, "f008-reviewer"))),null); }
        @Override public ServiceActionDescriptor descriptor() { return new ServiceActionDescriptor("f008.record",
                List.of(new ActionField("quantity",VariableValueType.LONG,true,false)),List.of(new ActionField("recordedQuantity",VariableValueType.LONG,true,false))); }
        @Override public ServiceActionResult execute(ServiceActionContext context,Map<String,WorkflowValue> inputs) {
            effect(context);
            awaitRelease();
            return new ServiceActionResult(Map.of("recordedQuantity",inputs.get("quantity")));
        }
        void awaitRelease() {
            var began=entered; var finish=release;
            if(began!=null && finish!=null) {
                began.countDown();
                try { if(!finish.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Test release timeout"); }
                catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }
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
