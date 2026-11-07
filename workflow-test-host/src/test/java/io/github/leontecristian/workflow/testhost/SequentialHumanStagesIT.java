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
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionContext;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionDescriptor;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionResult;
import io.github.leontecristian.workflow.core.dto.runtime.SingleAssignmentResult;
import io.github.leontecristian.workflow.core.dto.runtime.StartWorkflow;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowEvent;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowExecutionResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
import io.github.leontecristian.workflow.core.dto.tasktype.CreateTaskType;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import io.github.leontecristian.workflow.core.service.definition.DefinitionCapabilityProvider;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.runtime.SingleTaskAssignmentResolver;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowEventRecorder;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowExecutionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowServiceAction;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import io.github.leontecristian.workflow.testhost.configuration.integration.DatabaseIntegrationTest;
import io.github.leontecristian.workflow.testhost.repository.event.ConsoleEventStore;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Committed cross-stage boundaries and rollback on the guarded SQL Server only. */
@Import(SequentialHumanStagesIT.Adapters.class)
class SequentialHumanStagesIT extends DatabaseIntegrationTest {
    @Autowired WorkflowDefinitionService definitions;
    @Autowired TaskTypeCatalogService catalog;
    @Autowired WorkflowExecutionService engine;
    @Autowired WorkflowRuntimeQueryService reads;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired StageHost host;
    private final ObjectMapper json = new ObjectMapper();
    private final String marker = "f011.it." + UUID.randomUUID();
    private final String correlation = UUID.randomUUID().toString();
    private final String code = "F011_" + UUID.randomUUID().toString().replace("-", "");
    private final List<Long> instances = new ArrayList<>(), versions = new ArrayList<>();
    private Long family, taskType;
    private TransactionTemplate tx() { var tx = new TransactionTemplate(transactions); tx.setTimeout(30); return tx; }
    private WorkflowCommandContext context(String actor) { return new WorkflowCommandContext(ActorKind.USER, actor, Instant.now(), correlation); }
    @BeforeEach void setup() {
        host.code = code; host.failureNode = ""; host.failure = ""; host.failRecorder = false; host.distinct = false;
        host.assignments.clear(); host.entered = null; host.release = null;
        taskType = tx().execute(t -> catalog.create(new CreateTaskType(code, marker, null, true)).after().id());
    }
    @AfterEach void cleanup() {
        host.failRecorder = false; host.failureNode = "";
        if (host.release != null) host.release.countDown();
        tx().executeWithoutResult(t -> {
            for (long instance : instances) {
                jdbc.update("DELETE FROM wf_test.workflow_event_outbox WHERE instance_id=?",instance);
                jdbc.update("DELETE FROM wf.workflow_command_receipt WHERE instance_id=?",instance);
                jdbc.update("DELETE FROM wf.workflow_history WHERE instance_id=?",instance);
                jdbc.update("DELETE FROM wf.workflow_variable WHERE instance_id=?",instance);
                jdbc.update("DELETE FROM wf.workflow_task WHERE activity_id IN (SELECT id FROM wf.workflow_activity WHERE instance_id=?)",instance);
                jdbc.update("DELETE FROM wf.workflow_activity WHERE instance_id=?",instance);
                jdbc.update("DELETE FROM wf.workflow_instance WHERE id=?",instance);
            }
            jdbc.update("DELETE FROM wf_test.management_audit WHERE correlation_id=?",correlation);
            for (long version : versions) jdbc.update("DELETE FROM wf.workflow_definition_version WHERE id=?",version);
            if (family != null) jdbc.update("DELETE FROM wf.workflow_definition WHERE id=? AND workflow_key=?",family,marker);
            if (taskType != null) jdbc.update("DELETE FROM wf.workflow_task_type WHERE id=? AND code=?",taskType,code);
        });
    }
    private ObjectNode fixture(String name) throws Exception {
        Path path = Path.of("docs/features/F011-sequential-human-stages/"+name+".json");
        if (!Files.exists(path)) path = Path.of("..").resolve(path);
        var source = (ObjectNode)json.readTree(Files.readString(path)); source.put("key",marker);
        for (var node : source.path("nodes")) if (node.path("type").asText().equals("HUMAN_TASK")) {
            ((ObjectNode)node).put("taskType",code);
            ((ObjectNode)node.get("assignment")).put("resolver","f011.reviewer");
        }
        return source;
    }
    private long publish(ObjectNode source) {
        return tx().execute(t -> {
            var draft = definitions.importDraft(new ImportWorkflowDraft(source.toString()),new DefinitionManagementContext(marker,Instant.now())).after();
            family = draft.definitionId(); versions.add(draft.id());
            var result = definitions.publish(new CheckWorkflowDefinition(draft.id(),draft.lockVersion()),new DefinitionManagementContext(marker,Instant.now()));
            assertThat(result.published()).withFailMessage(()->result.report().toString()).isTrue(); return draft.id();
        });
    }
    private WorkflowExecutionResult start(long version) {
        var result = tx().execute(t -> engine.start(new StartWorkflow(UUID.randomUUID(),version,null,null,null),context("starter"))).execution();
        instances.add(result.instance().id()); return result;
    }
    private CompleteWorkflowTask command(long instance, long task, String outcome, String comment) {
        return new CompleteWorkflowTask(UUID.randomUUID(),instance,task,reads.findInstance(instance).orElseThrow().lockVersion(),outcome,comment);
    }
    private WorkflowCommandResult complete(CompleteWorkflowTask command, String actor) {
        return tx().execute(t -> engine.completeTask(command,context(actor)));
    }
    private long openTask(long instance) {
        return reads.listTasks(instance,0,100).items().stream().filter(t->t.status()==TaskStatus.OPEN).findFirst().orElseThrow().id();
    }
    private void fails(Runnable action, Category category) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(WorkflowExecutionException.class,e->assertThat(e.category()).isEqualTo(category));
    }
    private int count(String table, long id) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE instance_id=?",Integer.class,id); }
    private int effects() { return jdbc.queryForObject("SELECT COUNT(*) FROM wf_test.management_audit WHERE correlation_id=?",Integer.class,correlation); }
    private void unchanged(WorkflowExecutionResult before) {
        long id = before.instance().id();
        assertThat(reads.findInstance(id).orElseThrow()).isEqualTo(before.instance());
        assertThat(count("wf.workflow_history",id)).isEqualTo((int)before.lastHistorySequence());
        assertThat(count("wf_test.workflow_event_outbox",id)).isEqualTo((int)before.lastHistorySequence());
    }
    @ParameterizedTest @ValueSource(strings={"sequential-review","three-stage-review"})
    void directStagesPreserveCompletedIdentityHistoricalReadsAndReplay(String name) throws Exception {
        host.distinct = true;
        var source=fixture(name); var nodes=(ArrayNode)source.get("nodes"); nodes.add(nodes.remove(0));
        // Same task type and outcome name, different required-comment contract on the later node.
        for(var node:nodes) if(node.path("key").asText().equals("PARTNER")) ((ObjectNode)node.path("outcomes").get(0)).put("requiresComment",true);
        var started=start(publish(source)); long id=started.instance().id();
        assertThat(host.assignments).extracting(AssignmentContext::humanNodeKey).containsExactly("MANAGER");
        var first=command(id,started.task().id(),"APPROVE",null); var waiting=complete(first,"MANAGER").execution();
        assertThat(waiting.task().id()).isEqualTo(started.task().id()); assertThat(waiting.task().status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(waiting.instance().status()).isEqualTo(InstanceStatus.ACTIVE); assertThat(waiting.instance().currentNodeKey()).isEqualTo("PARTNER");
        assertThat(waiting.instance().resultKey()).isNull(); assertThat(waiting.instance().lockVersion()).isEqualTo(reads.findInstance(id).orElseThrow().lockVersion());
        assertThat(reads.findActivity(started.activity().id()).orElseThrow().nodeKey()).isEqualTo("MANAGER");
        assertThat(reads.findTaskSubmission(started.task().id()).orElseThrow().outcomeKey()).isEqualTo("APPROVE");
        assertThat(complete(first,"MANAGER").execution()).isEqualTo(waiting);
        assertThat(reads.listHistory(id,waiting.firstHistorySequence()-1,100).items()).extracting(h->h.eventType())
                .containsExactly("TASK_COMPLETED","ACTIVITY_COMPLETED","TRANSITION_TAKEN","ACTIVITY_ENTERED","TASK_CREATED");
        long second=openTask(id); assertThat(second).isNotEqualTo(started.task().id());
        assertThat(reads.findTask(second).orElseThrow().taskKey()).isEqualTo(started.task().taskKey());
        fails(()->complete(command(id,second,"APPROVE",null),"PARTNER"),Category.INVALID_INPUT);
        fails(()->complete(command(id,second,"APPROVE","reason"),"MANAGER"),Category.NOT_ASSIGNED);
        fails(()->complete(command(id,started.task().id(),"APPROVE",null),"MANAGER"),Category.INVALID_STATE);
        fails(()->complete(new CompleteWorkflowTask(first.commandId(),id,second,first.expectedLockVersion(),"APPROVE",null),"MANAGER"),Category.COMMAND_ID_CONFLICT);
        fails(()->complete(new CompleteWorkflowTask(first.commandId(),id,first.taskId(),first.expectedLockVersion(),"REJECT","reason"),"MANAGER"),Category.COMMAND_ID_CONFLICT);
        fails(()->complete(new CompleteWorkflowTask(first.commandId(),id,first.taskId(),first.expectedLockVersion(),"APPROVE","changed"),"MANAGER"),Category.COMMAND_ID_CONFLICT);
        fails(()->complete(first,"PARTNER"),Category.COMMAND_ID_CONFLICT);
        var progressed=complete(command(id,second,"APPROVE","exact later comment"),"PARTNER").execution();
        if(name.equals("three-stage-review")) {
            assertThat(progressed.instance().currentNodeKey()).isEqualTo("FINAL");
            progressed=complete(command(id,openTask(id),"REJECT","final rejection"),"FINAL").execution();
            assertThat(progressed.instance().resultKey()).isEqualTo("REJECTED");
        }
        assertThat(progressed.instance().status()).isEqualTo(InstanceStatus.COMPLETED);
        assertThat(complete(first,"MANAGER").execution()).isEqualTo(waiting);
        int stages=name.equals("three-stage-review")?3:2;
        assertThat(host.assignments).hasSize(stages);
        assertThat(count("wf.workflow_activity",id)).isEqualTo(stages);
        assertThat(count("wf.workflow_command_receipt",id)).isEqualTo(stages+1);
        var sequences=jdbc.queryForList("SELECT sequence_number FROM wf.workflow_history WHERE instance_id=? ORDER BY sequence_number",Long.class,id);
        assertThat(sequences).containsExactlyElementsOf(java.util.stream.LongStream.rangeClosed(1,progressed.lastHistorySequence()).boxed().toList());
        assertThat(count("wf_test.workflow_event_outbox",id)).isEqualTo(sequences.size());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_activity a JOIN wf.workflow_history h ON a.instance_id=h.instance_id AND a.activation_sequence=h.sequence_number WHERE a.instance_id=? AND h.activity_id=a.id AND h.event_type='ACTIVITY_ENTERED'",Integer.class,id)).isEqualTo(stages);
    }
    private ObjectNode withEffects(String name) throws Exception {
        var source=fixture(name); var nodes=(ArrayNode)source.get("nodes"); var edges=(ArrayNode)source.get("transitions");
        source.putArray("variables").addObject().put("key","data").put("type","JSON").put("nullable",false);
        ((ObjectNode)source.get("startPolicy")).putObject("initialize").putObject("data").put("source","literal").putObject("value").put("before",true);
        var service=nodes.addObject().put("key","EFFECT").put("type","SERVICE_TASK").put("name","Effect").put("action","f011.effect");
        service.putObject("inputs"); service.putObject("outputs").putObject("data").put("source","actionOutput").put("key","data");
        for(var edge:edges) if(edge.path("key").asText().equals("managerApprove")) ((ObjectNode)edge).put("to","EFFECT");
        edges.addObject().put("key","effect").put("from","EFFECT").put("to","PARTNER"); return source;
    }
    @ParameterizedTest @ValueSource(strings={"exception","empty","multiple","missing","recorder","service","output","routing"})
    void downstreamFailureRollsBackSubmissionSuccessorJsonHostEffectReceiptAndHandoff(String failure) throws Exception {
        var source=withEffects("sequential-review");
        if(failure.equals("routing")) {
            ((ArrayNode)source.get("nodes")).addObject().put("key","ROUTE").put("type","EXCLUSIVE_GATEWAY").put("name","Fail after replacement");
            var edges=(ArrayNode)source.get("transitions");
            for(var edge:edges) if(edge.path("from").asText().equals("EFFECT")) ((ObjectNode)edge).put("to","ROUTE");
            edges.addObject().put("key","evaluated").put("from","ROUTE").put("to","PARTNER").put("condition","1 / 0 > 0");
            edges.addObject().put("key","default").put("from","ROUTE").put("to","APPROVED").put("default",true);
        }
        var started=start(publish(source)); long id=started.instance().id();
        host.failureNode="PARTNER"; host.failure=failure; host.failRecorder=failure.equals("recorder");
        assertThatThrownBy(()->complete(command(id,started.task().id(),"APPROVE",null),"reviewer")).isInstanceOf(WorkflowExecutionException.class);
        unchanged(started); assertThat(openTask(id)).isEqualTo(started.task().id());
        assertThat(count("wf.workflow_activity",id)).isEqualTo(1); assertThat(count("wf.workflow_command_receipt",id)).isEqualTo(1);
        assertThat(reads.findTaskSubmission(started.task().id())).isEmpty(); assertThat(effects()).isZero();
        assertThat(jdbc.queryForObject("SELECT json_value FROM wf.workflow_variable WHERE instance_id=?",String.class,id)).isEqualTo("{\"before\":true}");
        host.failureNode=""; host.failRecorder=false;
        if(failure.equals("routing")) return;
        tx().executeWithoutResult(t->{
            engine.completeTask(command(id,started.task().id(),"APPROVE",null),context("reviewer"));
            assertThat(reads.findTaskSubmission(started.task().id())).isPresent();
            assertThat(count("wf.workflow_activity",id)).isEqualTo(2);
            t.setRollbackOnly(); // Host failure after core success must also undo the successor.
        });
        unchanged(started); assertThat(effects()).isZero();
    }
    @Test void thirdStageAssignmentFailurePreservesBothEarlierCommandBoundaries() throws Exception {
        var started=start(publish(fixture("three-stage-review"))); long id=started.instance().id();
        var waiting=complete(command(id,started.task().id(),"APPROVE",null),"reviewer").execution(); long second=openTask(id);
        host.failureNode="FINAL"; host.failure="empty";
        fails(()->complete(command(id,second,"APPROVE",null),"reviewer"),Category.INVALID_ASSIGNMENT);
        unchanged(waiting); assertThat(openTask(id)).isEqualTo(second); assertThat(reads.findTaskSubmission(started.task().id())).isPresent();
        assertThat(count("wf.workflow_command_receipt",id)).isEqualTo(2);
    }
    @Test void failedStartLeavesNoStageOrReceipt() throws Exception {
        long version=publish(fixture("sequential-review")); host.failureNode="MANAGER"; host.failure="empty";
        fails(()->start(version),Category.INVALID_ASSIGNMENT);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_instance WHERE definition_version_id=?",Integer.class,version)).isZero();
    }
    @Test void publishedPinsAndLaterEntrySurviveNewPublicationAndDisabledSelectability() throws Exception {
        var source=fixture("sequential-review"); long original=publish(source); var started=start(original);
        source.put("version",2);
        ((ObjectNode)source.path("transitions").get(1)).put("to","APPROVED");
        // Keep PARTNER reachable on the new rejection route; old instances retain the original routes.
        ((ObjectNode)source.path("transitions").get(2)).put("to","PARTNER");
        long newer=publish(source);
        tx().executeWithoutResult(t->{var row=catalog.findByCode(code).orElseThrow(); catalog.update(new io.github.leontecristian.workflow.core.dto.tasktype.UpdateTaskType(row.id(),row.lockVersion(),row.name(),row.description(),false));});
        var waiting=complete(command(started.instance().id(),started.task().id(),"APPROVE",null),"reviewer").execution();
        assertThat(waiting.instance().currentNodeKey()).isEqualTo("PARTNER");
        assertThat(waiting.instance().definitionVersionId()).isEqualTo(original);
        var future=start(original); assertThat(future.instance().currentNodeKey()).isEqualTo("MANAGER");
        var fresh=start(newer);
        assertThat(complete(command(fresh.instance().id(),fresh.task().id(),"APPROVE",null),"reviewer").execution().instance().status()).isEqualTo(InstanceStatus.COMPLETED);
    }
    @Test void competingCommandsCreateOnlyOneSuccessorAndStaleCommandCannotAdvanceIt() throws Exception {
        var started=start(publish(withEffects("sequential-review"))); long id=started.instance().id();
        var first=command(id,started.task().id(),"APPROVE",null); var second=command(id,started.task().id(),"APPROVE",null);
        host.entered=new CountDownLatch(1); host.release=new CountDownLatch(1); var executor=Executors.newFixedThreadPool(2);
        try {
            var winner=executor.submit(()->complete(first,"reviewer")); assertThat(host.entered.await(10,TimeUnit.SECONDS)).isTrue();
            var began=new CountDownLatch(1);
            var loser=executor.submit(()->tx().execute(t->{began.countDown(); return engine.completeTask(second,context("reviewer"));}));
            assertThat(began.await(10,TimeUnit.SECONDS)).isTrue(); host.release.countDown();
            var waiting=winner.get(20,TimeUnit.SECONDS).execution();
            assertThatThrownBy(()->loser.get(20,TimeUnit.SECONDS)).hasCauseInstanceOf(WorkflowExecutionException.class);
            assertThat(count("wf.workflow_activity",id)).isEqualTo(2); assertThat(effects()).isEqualTo(1); assertThat(host.assignments).hasSize(2);
            fails(()->complete(new CompleteWorkflowTask(UUID.randomUUID(),id,openTask(id),started.instance().lockVersion(),"APPROVE",null),"reviewer"),Category.STALE_VERSION);
            assertThat(complete(first,"reviewer").execution()).isEqualTo(waiting);
        } finally { host.release.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void alternativeFirstStagesResolveOnlyTheReachedNodeAndMergeAtFinal(boolean selectManager) throws Exception {
        var source=fixture("three-stage-review"); var nodes=(ArrayNode)source.get("nodes"); var edges=(ArrayNode)source.get("transitions");
        nodes.addObject().put("key","GATE").put("type","EXCLUSIVE_GATEWAY").put("name","First review");
        ((ObjectNode)edges.get(0)).put("to","GATE"); ((ObjectNode)edges.get(1)).put("to","FINAL");
        edges.addObject().put("key","manager").put("from","GATE").put("to","MANAGER").put("condition",Boolean.toString(selectManager));
        edges.addObject().put("key","partner").put("from","GATE").put("to","PARTNER").put("default",true);
        var started=start(publish(source)); assertThat(started.instance().currentNodeKey()).isEqualTo(selectManager?"MANAGER":"PARTNER");
        assertThat(host.assignments).extracting(AssignmentContext::humanNodeKey).containsExactly(selectManager?"MANAGER":"PARTNER");
        var waiting=complete(command(started.instance().id(),started.task().id(),"APPROVE",null),"reviewer").execution();
        assertThat(waiting.instance().currentNodeKey()).isEqualTo("FINAL");
        assertThat(host.assignments).hasSize(2);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void optionalStageResolverRunsOnlyForReachedBranch(boolean selectPartner) throws Exception {
        var source=fixture("sequential-review"); var nodes=(ArrayNode)source.get("nodes"); var edges=(ArrayNode)source.get("transitions");
        nodes.addObject().put("key","ROUTE").put("type","EXCLUSIVE_GATEWAY").put("name","Optional stage");
        ((ObjectNode)edges.get(1)).put("to","ROUTE");
        edges.addObject().put("key","selected").put("from","ROUTE").put("to","PARTNER").put("condition",Boolean.toString(selectPartner));
        edges.addObject().put("key","fallback").put("from","ROUTE").put("to","APPROVED").put("default",true);
        var started=start(publish(source)); var result=complete(command(started.instance().id(),started.task().id(),"APPROVE",null),"reviewer").execution();
        assertThat(host.assignments).hasSize(selectPartner?2:1);
        assertThat(result.instance().status()).isEqualTo(selectPartner?InstanceStatus.ACTIVE:InstanceStatus.COMPLETED);
    }
    @ParameterizedTest @ValueSource(ints={50,51})
    void allowanceResetsAtEachHumanBoundary(int visits) throws Exception {
        var source=fixture("three-stage-review"); var nodes=(ArrayNode)source.get("nodes"); var edges=(ArrayNode)source.get("transitions");
        for(String stage:List.of("MANAGER","PARTNER")) {
            String next=stage.equals("MANAGER")?"PARTNER":"FINAL";
            for(var edge:edges) if(edge.path("from").asText().equals(stage)&&edge.path("outcome").asText().equals("APPROVE")) ((ObjectNode)edge).put("to",stage+"0");
            for(int i=0;i<visits;i++) {
                nodes.addObject().put("key",stage+i).put("type","EXCLUSIVE_GATEWAY").put("name","Bounded visit");
                edges.addObject().put("key","edge"+stage+i).put("from",stage+i).put("to",i==visits-1?next:stage+(i+1)).put("default",true);
            }
        }
        var started=start(publish(source)); long id=started.instance().id();
        if(visits==51) { fails(()->complete(command(id,started.task().id(),"APPROVE",null),"reviewer"),Category.LIMIT_EXCEEDED); unchanged(started); }
        else {
            complete(command(id,started.task().id(),"APPROVE",null),"reviewer");
            var finalWait=complete(command(id,openTask(id),"APPROVE",null),"reviewer").execution();
            assertThat(finalWait.instance().currentNodeKey()).isEqualTo("FINAL");
            assertThat(complete(command(id,openTask(id),"APPROVE",null),"reviewer").execution().instance().status()).isEqualTo(InstanceStatus.COMPLETED);
        }
    }
    @TestConfiguration static class Adapters {
        @Bean StageHost stageHost(JdbcTemplate jdbc, ConsoleEventStore outbox, ConsoleWire wire, org.springframework.beans.factory.ObjectProvider<WorkflowRuntimeQueryService> reads) { return new StageHost(jdbc,outbox,wire,reads); }
    }
    static class StageHost implements DefinitionCapabilityProvider, SingleTaskAssignmentResolver, WorkflowServiceAction, WorkflowEventRecorder {
        @Override public Set<io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind> targetKinds() { return Set.of(io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind.USER); }
        volatile String code, failureNode="", failure="";
        volatile boolean failRecorder, distinct;
        volatile CountDownLatch entered, release;
        final List<AssignmentContext> assignments=new CopyOnWriteArrayList<>();
        private final JdbcTemplate jdbc; private final ConsoleEventStore outbox; private final ConsoleWire wire; private final org.springframework.beans.factory.ObjectProvider<WorkflowRuntimeQueryService> readers;
        StageHost(JdbcTemplate jdbc,ConsoleEventStore outbox,ConsoleWire wire,org.springframework.beans.factory.ObjectProvider<WorkflowRuntimeQueryService> readers) { this.jdbc=jdbc; this.outbox=outbox; this.wire=wire; this.readers=readers; }
        public String identifier() { return "f011.reviewer"; }
        public DefinitionCapabilities snapshot() { return new DefinitionCapabilities(Set.of(new SingleAssignmentCapability(identifier(), Set.of(io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind.USER))),code==null?Set.of():Set.of(new TaskTypeCapability(code,Set.of("APPROVE","REJECT")))); }
        public SingleAssignmentResult resolve(AssignmentContext context) {
            assignments.add(context);
            if(context.humanNodeKey().equals(failureNode)) switch(failure) {
                case "exception" -> throw new IllegalStateException("Assignment failure");
                case "empty" -> { return new SingleAssignmentResult(List.of(),null); }
                case "multiple" -> { return new SingleAssignmentResult(List.of(new ResolvedTask("review", new io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget(io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind.USER, "reviewer")),new ResolvedTask("other", new io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget(io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind.USER, "reviewer"))),null); }
                case "missing" -> { return new SingleAssignmentResult(List.of(new ResolvedTask("review",null)),null); }
            }
            return new SingleAssignmentResult(List.of(new ResolvedTask("review", new io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget(io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind.USER, distinct?context.humanNodeKey():"reviewer"))),"revision-"+context.humanNodeKey());
        }
        public ServiceActionDescriptor descriptor() { return new ServiceActionDescriptor("f011.effect",List.of(),List.of(new ActionField("data",VariableValueType.JSON,true,false))); }
        public ServiceActionResult execute(ServiceActionContext context,Map<String,WorkflowValue> inputs) {
            var reads = readers.getObject();
            var submissions=reads.listTasks(context.instanceId(),0,100).items().stream().filter(t->t.status()==TaskStatus.COMPLETED).toList();
            assertThat(submissions).hasSize(1); assertThat(reads.findTaskSubmission(submissions.get(0).id())).isPresent();
            jdbc.update("INSERT INTO wf_test.management_audit(occurred_at,actor_ref,operation,entity_kind,entity_id,correlation_id,evidence_json) VALUES(SYSUTCDATETIME(),?,'F011_EFFECT','TASK_TYPE',1,?,N'{}')",context.commandContext().actorRef(),context.commandContext().correlationId());
            if(!failureNode.isEmpty() && failure.equals("service")) throw new IllegalStateException("Service failure after host write");
            if(!failureNode.isEmpty() && failure.equals("output")) return new ServiceActionResult(Map.of());
            if(entered!=null) {
                entered.countDown();
                try { if(!release.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Release timeout"); }
                catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }
            return new ServiceActionResult(Map.of("data",new WorkflowValue(VariableValueType.JSON,WorkflowJsonValue.parse("{\"after\":true}"))));
        }
        public void record(List<WorkflowEvent> events) {
            for(var event:events) outbox.record(event,wire.json(event));
            if(failRecorder) throw new IllegalStateException("Recorder failure after successor creation");
        }
    }
}
