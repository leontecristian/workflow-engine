package io.github.leontecristian.workflow.testhost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leontecristian.workflow.core.dto.definition.CheckWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionManagementContext;
import io.github.leontecristian.workflow.core.dto.definition.ImportWorkflowDraft;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowExecutionResult;
import io.github.leontecristian.workflow.core.dto.tasktype.CreateTaskType;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowExecutionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings;
import io.github.leontecristian.workflow.testhost.configuration.integration.DatabaseIntegrationTest;
import io.github.leontecristian.workflow.testhost.dto.runtime.SandboxStart;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeTransaction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

/** One guarded DB setup exercises the moved mapping, host transaction and optional browser fixture. */
@SpringBootTest(classes=WorkflowTestHostApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"workflow.console.enabled=true","workflow.console.capabilities=sample",
        "workflow.console.admin.username=f016-admin","workflow.console.admin.password=f016-local-test-password",
        "workflow.console.reviewer.username=f016-reviewer","workflow.console.reviewer.password=f016-local-test-password"})
@Import(SharedAssignmentIT.FailureFixture.class)
class SharedAssignmentIT extends DatabaseIntegrationTest {
    @Autowired WorkflowDefinitionService definitions;
    @Autowired WorkflowRuntimeQueryService reads;
    @Autowired TaskTypeCatalogService catalog;
    @Autowired SandboxRequestStore requests;
    @Autowired ConsoleRuntimeTransaction runtime;
    @Autowired AtomicBoolean failCompletion;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    io.github.leontecristian.workflow.testhost.service.runtime.SandboxSharedAssignmentProvider membership;
    private final io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget target =
        io.github.leontecristian.workflow.testhost.service.runtime.SandboxSharedAssignmentProvider.FINANCE;
    @Autowired JdbcTemplate jdbc;
    @Autowired io.github.leontecristian.workflow.testhost.service.event.ConsoleEventDelivery delivery;
    @Autowired PlatformTransactionManager transactions;
    @Autowired WebApplicationContext web;
    @LocalServerPort int port;
    private final ObjectMapper json=new ObjectMapper();
    private final String marker="f016."+UUID.randomUUID();
    private final List<Long> instances=new ArrayList<>();
    private Long versionId,familyId,ownedTaskType;
    private TransactionTemplate tx() { return new TransactionTemplate(transactions); }
    private Path repository() { return Files.isDirectory(Path.of("docs"))?Path.of(""):Path.of(".."); }
    private WorkflowCommandContext context() { return new WorkflowCommandContext(ActorKind.USER,"f016-admin",Instant.now(),marker); }

    /** Test-only selection of the existing post-result failure under the real transaction proxy.
     * Start and ordinary completion still call the production transaction body unchanged.
     */
    @TestConfiguration(proxyBeanMethods=false)
    static class FailureFixture {
        @Bean AtomicBoolean failCompletion() { return new AtomicBoolean(); }
        @Bean @Primary ConsoleRuntimeTransaction fixtureRuntime(WorkflowExecutionService execution,
                WorkflowRuntimeQueryService reads,SandboxRequestStore requests,ConsoleSettings settings,AtomicBoolean failCompletion) {
            var failureSettings=new ConsoleSettings(new MockEnvironment().withProperty("workflow.console.enabled","true")
                .withProperty("workflow.console.admin.username",settings.admin).withProperty("workflow.console.admin.password",settings.adminPassword)
                .withProperty("workflow.console.reviewer.username",settings.reviewer).withProperty("workflow.console.reviewer.password",settings.reviewerPassword)
                .withProperty("workflow.console.fail-runtime-after-result","true"));
            var failed=new ConsoleRuntimeTransaction(execution,reads,requests,failureSettings);
            return new ConsoleRuntimeTransaction(execution,reads,requests,settings) {
                @Override @Transactional public WorkflowCommandResult complete(CompleteWorkflowTask command,WorkflowCommandContext context) {
                    return failCompletion.get()?failed.complete(command,context):super.complete(command,context);
                }
            };
        }
    }
    private MockHttpSession session(String actor,String role) {
        var s=new MockHttpSession();s.setAttribute("SPRING_SECURITY_CONTEXT",new SecurityContextImpl(
            UsernamePasswordAuthenticationToken.authenticated(actor,"unused",List.of(new SimpleGrantedAuthority(role)))));return s;
    }
    private WorkflowExecutionResult start() {
        long request=tx().execute(t->requests.create(marker,"{\"amount\":9007199254740993.00}",marker,Instant.now())).id();
        var result=runtime.start(new SandboxStart(UUID.randomUUID(),versionId,request,0),context()).execution();
        instances.add(result.instance().id());return result;
    }
    private String completion(WorkflowExecutionResult started,UUID command) throws Exception {
        return json.writeValueAsString(Map.of("commandId",command.toString(),"outcomeKey","APPROVE"));
    }
    private String replayBody(WorkflowExecutionResult started,UUID command) throws Exception {
        return json.writeValueAsString(Map.of("commandId",command.toString(),"outcomeKey","APPROVE",
            "instanceId",Long.toString(started.instance().id()),"taskId",Long.toString(started.task().id())));
    }
    private void prepare(String resolver) throws Exception {
        if(catalog.findByCode("SANDBOX_REVIEW").isEmpty()) ownedTaskType=tx().execute(t->catalog.create(new CreateTaskType("SANDBOX_REVIEW",marker,null,true)).after().id());
        var document=(ObjectNode)json.readTree(Files.readString(repository().resolve("docs/features/F006-test-host-definition-console/request-status-review.json")));
        document.put("key",marker);
        for(var node:document.path("nodes")) if(node.path("type").asText().equals("HUMAN_TASK")) ((ObjectNode)node.path("assignment")).put("resolver",resolver);
        tx().executeWithoutResult(t->{
            var draft=definitions.importDraft(new ImportWorkflowDraft(document.toString()),new DefinitionManagementContext(marker,Instant.now())).after();
            versionId=draft.id();familyId=draft.definitionId();
            var published=definitions.publish(new CheckWorkflowDefinition(versionId,draft.lockVersion()),new DefinitionManagementContext(marker,Instant.now()));
            assertThat(published.published()).withFailMessage(()->published.report().toString()).isTrue();
        });
    }
    private WorkflowCommandContext actor(String actor) { return new WorkflowCommandContext(ActorKind.USER,actor,Instant.now(),marker); }
    private CompleteWorkflowTask command(WorkflowExecutionResult started,String outcome) {
        return new CompleteWorkflowTask(UUID.randomUUID(),started.instance().id(),started.task().id(),started.instance().lockVersion(),outcome,"test");
    }
    private void denied(Runnable action,io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category category) {
        org.assertj.core.api.Assertions.assertThatThrownBy(action::run).isInstanceOfSatisfying(
            io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.class,e->assertThat(e.category()).isEqualTo(category));
    }
    @Test void currentMembershipRollbackReplayAndEvidence() throws Exception {
        prepare("sandbox.sharedGroupReviewer");
        membership.replaceMembers(target,java.util.Set.of("f016-reviewer","f016-reviewer2"));
        var started=start();var command=command(started,"APPROVE");
        assertThat(started.task().assignment()).isEqualTo(target);
        for(String who:List.of("f016-reviewer","f016-reviewer2"))
            assertThat(reads.listEligibleTasks(who,0,100,t->t.id()==started.task().id()).items()).extracting(t->t.id()).containsExactly(started.task().id());
        membership.replaceMembers(target,java.util.Set.of("f016-reviewer2"));
        denied(()->runtime.complete(command,actor("f016-reviewer")),io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category.NOT_ASSIGNED);
        assertThat(reads.findInstance(started.instance().id()).orElseThrow().lockVersion()).isEqualTo(started.instance().lockVersion());
        membership.replaceMembers(target,java.util.Set.of("f016-reviewer"));
        failCompletion.set(true);
        org.assertj.core.api.Assertions.assertThatThrownBy(()->runtime.complete(command,actor("f016-reviewer"))).isInstanceOf(IllegalStateException.class);
        failCompletion.set(false);
        assertThat(reads.findTaskSubmission(started.task().id())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_command_receipt WHERE command_id=?",Integer.class,command.commandId())).isZero();
        org.mockito.Mockito.doAnswer(invocation->{
            boolean allowed=(Boolean)invocation.callRealMethod();
            membership.replaceMembers(target,java.util.Set.of());
            return allowed;
        }).when(membership).isCurrentMember(org.mockito.ArgumentMatchers.any());
        var accepted=runtime.complete(command,actor("f016-reviewer"));
        org.mockito.Mockito.reset(membership);
        assertThat(accepted.execution().task().closedBy()).isEqualTo("f016-reviewer");
        assertThat(accepted.execution().task().assignment()).isEqualTo(target);
        assertThat(runtime.replayComplete(command,actor("f016-reviewer")).orElseThrow().replayed()).isTrue();
        denied(()->runtime.replayComplete(command,actor("f016-reviewer2")),io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category.COMMAND_ID_CONFLICT);
        var detail=reads.listHistory(started.instance().id(),0,100).items().stream().filter(h->h.eventType().equals("TASK_CREATED")).findFirst().orElseThrow();
        assertThat(detail.detailsSchemaVersion()).isEqualTo((short)3);
        assertThat(reads.findHistoryEvent(started.instance().id(),detail.historySequence()).orElseThrow().detailsJson()).contains("assignment","GROUP","sandbox.finance");
        assertThat(jdbc.queryForObject("SELECT result_schema_version FROM wf.workflow_command_receipt WHERE command_id=?",Integer.class,command.commandId())).isEqualTo(4);
        var pending=start();
        denied(()->runtime.complete(command(pending,"APPROVE"),actor("f016-reviewer")),io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category.NOT_ASSIGNED);
        membership.removeTarget(target);
        assertThat(reads.findTask(pending.task().id()).orElseThrow().status()).isEqualTo(io.github.leontecristian.workflow.core.dto.runtime.TaskStatus.OPEN);
        denied(()->start(),io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category.INVALID_ASSIGNMENT);
        membership.replaceMembers(target,java.util.Set.of("f016-reviewer"));
        if(Boolean.getBoolean("f016.browserHold")) browserHold(pending);
    }
    @Test void competingMembersCommitOnceAndRoleRoundTrips() throws Exception {
        prepare("sandbox.sharedRoleReviewer");
        var started=start();var one=command(started,"APPROVE");var two=command(started,"REJECT");
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var gate=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Object> a=()->{gate.await();try{return runtime.complete(one,actor("f016-reviewer"));}catch(RuntimeException failure){return failure;}};
            java.util.concurrent.Callable<Object> b=()->{gate.await();try{return runtime.complete(two,actor("f016-reviewer2"));}catch(RuntimeException failure){return failure;}};
            var fa=pool.submit(a);var fb=pool.submit(b);gate.countDown();
            var results=List.of(fa.get(30,TimeUnit.SECONDS),fb.get(30,TimeUnit.SECONDS));
            assertThat(results.stream().filter(r->r instanceof WorkflowCommandResult).count()).isEqualTo(1);
            assertThat(results.stream().filter(r->r instanceof RuntimeException).count()).isEqualTo(1);
            assertThat(reads.findTask(started.task().id()).orElseThrow().assignment().kind()).isEqualTo(io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind.ROLE);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_history WHERE task_id=? AND event_type='TASK_COMPLETED'",Integer.class,started.task().id())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_command_receipt WHERE instance_id=? AND operation_type='COMPLETE_TASK'",Integer.class,started.instance().id())).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }
    @Test void retainedUserEvidenceAndMigrationBackfill() throws Exception {
        // Exercise the exact migration SQL on a connection-local pre-F016 table with representative rows.
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection->{
            try(var statement=connection.createStatement()) {
                statement.execute("CREATE TABLE #f016_task(id BIGINT NOT NULL, activity_id BIGINT NOT NULL, status VARCHAR(20) NOT NULL, assigned_user_ref NVARCHAR(200) COLLATE Latin1_General_100_BIN2 NOT NULL,closed_by NVARCHAR(200) NULL)");
                statement.execute("CREATE INDEX ix_workflow_task_open_assignee ON #f016_task(assigned_user_ref,id) WHERE status='OPEN'");
                statement.execute("INSERT INTO #f016_task VALUES(101,201,'OPEN',N'Alice ',NULL),(102,202,'COMPLETED',N'Ștefan',N'Ștefan')");
                var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();
                try(var input=getClass().getClassLoader().getResourceAsStream("db/changelog/workflow/changes/004-shared-task-assignment.xml")) {
                    var sql=factory.newDocumentBuilder().parse(input).getElementsByTagName("sql");
                    for(int i=0;i<sql.getLength();i++) statement.execute(sql.item(i).getTextContent().replace("wf.workflow_task","#f016_task"));
                }
                try(var rows=statement.executeQuery("SELECT id,assignment_kind,assignment_ref,closed_by,DATALENGTH(assignment_ref) FROM #f016_task ORDER BY id")) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getLong(1)).isEqualTo(101);
                    assertThat(rows.getString(2)).isEqualTo("USER"); assertThat(rows.getString(3)).isEqualTo("Alice ");assertThat(rows.getInt(5)).isEqualTo(12);
                    assertThat(rows.next()).isTrue();assertThat(rows.getString(3)).isEqualTo("Ștefan");assertThat(rows.getString(4)).isEqualTo("Ștefan");
                    assertThat(rows.next()).isFalse();
                }
                statement.execute("DROP TABLE #f016_task");
            } catch(Exception failure) {throw new RuntimeException(failure);}
            return null;
        });
        prepare("sandbox.singleReviewer");
        var current=start();
        var currentCreation=reads.listHistory(current.instance().id(),0,100).items().stream().filter(h->h.eventType().equals("TASK_CREATED")).findFirst().orElseThrow();
        String currentEventId=current.instance().id()+":"+currentCreation.historySequence();
        String currentJson=jdbc.queryForObject("SELECT event_json FROM wf_test.workflow_event_outbox WHERE event_id=?",String.class,currentEventId);
        assertThat(json.readTree(currentJson).path("eventSchemaVersion").asInt()).isEqualTo(2);
        assertThat(json.readTree(currentJson).path("payload").path("assignmentKind").asText()).isEqualTo("USER");
        delivery.redeliver(currentEventId);
        assertThat(jdbc.queryForObject("SELECT event_json FROM wf_test.workflow_event_inbox WHERE event_id=?",String.class,currentEventId)).isEqualTo(currentJson);
        var started=start();
        var creation=reads.listHistory(started.instance().id(),0,100).items().stream().filter(h->h.eventType().equals("TASK_CREATED")).findFirst().orElseThrow();
        String oldCreation=json.writeValueAsString(Map.of("taskKey",started.task().taskKey(),"assignedUserRef","f016-reviewer"));
        String legacyEventId=started.instance().id()+":"+creation.historySequence();
        var legacyEvent=(ObjectNode)json.readTree(jdbc.queryForObject("SELECT event_json FROM wf_test.workflow_event_outbox WHERE event_id=?",String.class,legacyEventId));
        legacyEvent.put("eventSchemaVersion",1);legacyEvent.set("payload",json.readTree(oldCreation));
        String legacyEventBytes=legacyEvent.toString();
        jdbc.update("UPDATE wf_test.workflow_event_outbox SET event_json=? WHERE event_id=?",legacyEventBytes,legacyEventId);
        delivery.redeliver(legacyEventId);
        assertThat(jdbc.queryForObject("SELECT event_json FROM wf_test.workflow_event_inbox WHERE event_id=?",String.class,legacyEventId)).isEqualTo(legacyEventBytes);
        assertThat(jdbc.queryForObject("SELECT event_json FROM wf_test.workflow_event_outbox WHERE event_id=?",String.class,legacyEventId)).isEqualTo(legacyEventBytes);
        jdbc.update("UPDATE wf.workflow_history SET details_schema_version=2,details_json=? WHERE instance_id=? AND sequence_number=?",oldCreation,started.instance().id(),creation.historySequence());
        var command=command(started,"APPROVE");var accepted=runtime.complete(command,actor("f016-reviewer"));
        var legacy=(ObjectNode)json.readTree(jdbc.queryForObject("SELECT result_json FROM wf.workflow_command_receipt WHERE command_id=?",String.class,command.commandId()));
        var task=(ObjectNode)legacy.path("task");task.remove("assignment");task.put("assignedUserRef","f016-reviewer");
        jdbc.update("UPDATE wf.workflow_command_receipt SET result_schema_version=3,result_json=? WHERE command_id=?",legacy.toString(),command.commandId());
        assertThat(runtime.replayComplete(command,actor("f016-reviewer")).orElseThrow().execution()).isEqualTo(accepted.execution());
        assertThat(jdbc.queryForObject("SELECT details_json FROM wf.workflow_history WHERE instance_id=? AND sequence_number=?",String.class,started.instance().id(),creation.historySequence())).isEqualTo(oldCreation);
    }
    private void browserHold(WorkflowExecutionResult pending) throws Exception {
        Path local=repository().resolve(".local");Files.createDirectories(local);
        Files.writeString(local.resolve("f016-runtime.json"),json.writeValueAsString(Map.of("port",port,"pid",ProcessHandle.current().pid(),
                "instance",pending.instance().id(),"task",pending.task().id(),"activity",pending.task().activityId(),"version",versionId)));
        Path done=local.resolve("f016-browser.done");long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(20);
        while(!Files.exists(done)&&System.nanoTime()<deadline) Thread.sleep(250);
        assertThat(Files.exists(done)).as("Focused browser completion signal").isTrue();Files.deleteIfExists(done);
    }
    @AfterEach void cleanup() {
        failCompletion.set(false);
        tx().executeWithoutResult(t->{
            for(long id:instances) {
                jdbc.update("DELETE FROM wf_test.workflow_event_inbox WHERE event_id IN (SELECT event_id FROM wf_test.workflow_event_outbox WHERE instance_id=?)",id);
                jdbc.update("DELETE FROM wf_test.workflow_event_outbox WHERE instance_id=?",id);
                jdbc.update("DELETE FROM wf.workflow_command_receipt WHERE instance_id=?",id);
                jdbc.update("DELETE FROM wf.workflow_history WHERE instance_id=?",id);
                jdbc.update("DELETE FROM wf.workflow_variable WHERE instance_id=?",id);
                jdbc.update("DELETE FROM wf.workflow_task WHERE activity_id IN (SELECT id FROM wf.workflow_activity WHERE instance_id=?)",id);
                jdbc.update("DELETE FROM wf.workflow_activity WHERE instance_id=?",id);
                jdbc.update("DELETE FROM wf.workflow_instance WHERE id=?",id);
            }
            jdbc.update("DELETE FROM wf_test.sandbox_request WHERE name=? AND created_by=?",marker,marker);
            if(versionId!=null)jdbc.update("DELETE FROM wf.workflow_definition_version WHERE id=?",versionId);
            if(familyId!=null)jdbc.update("DELETE FROM wf.workflow_definition WHERE id=? AND workflow_key=?",familyId,marker);
            if(ownedTaskType!=null)jdbc.update("DELETE FROM wf.workflow_task_type WHERE id=? AND code='SANDBOX_REVIEW'",ownedTaskType);
        });
    }
}
