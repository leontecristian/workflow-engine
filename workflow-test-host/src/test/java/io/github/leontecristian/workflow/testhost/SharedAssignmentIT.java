package io.github.leontecristian.workflow.testhost;

import static org.assertj.core.api.Assertions.assertThat;
import static io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category.ADMIN_RELEASE_FORBIDDEN;
import static io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category.CLAIM_REQUIRED;
import static io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category.COMMAND_ID_CONFLICT;
import static io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category.CONCURRENT_COMMAND;
import static io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category.NOT_ASSIGNED;
import static io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category.NOT_FOUND;
import static io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category.STALE_VERSION;
import static io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category.TASK_ALREADY_ASSIGNED;
import static io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category.TASK_ASSIGNED_TO_OTHER;
import static io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category.TASK_NOT_ASSIGNED;
import static io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category.TASK_NOT_CLAIMABLE;
import io.github.leontecristian.workflow.core.dto.runtime.AdministrativelyReleaseWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.ClaimWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.ReleaseWorkflowTask;
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
    @Autowired WorkflowExecutionService execution;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    io.github.leontecristian.workflow.core.service.runtime.WorkflowEventRecorder recorder;
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
        prepare(resolver, null);
    }
    private void prepare(String resolver, String policy) throws Exception {
        if(catalog.findByCode("SANDBOX_REVIEW").isEmpty()) ownedTaskType=tx().execute(t->catalog.create(new CreateTaskType("SANDBOX_REVIEW",marker,null,true)).after().id());
        var document=(ObjectNode)json.readTree(Files.readString(repository().resolve("docs/features/F006-test-host-definition-console/request-status-review.json")));
        document.put("key",marker);
        for (var node : document.path("nodes")) {
            if ("HUMAN_TASK".equals(node.path("type").asText())) {
                ((ObjectNode) node.path("assignment")).put("resolver", resolver);
                if (policy != null) ((ObjectNode) node).put("claimPolicy", policy);
            }
        }
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
        assertThat(detail.detailsSchemaVersion()).isEqualTo((short)4);
        assertThat(reads.findHistoryEvent(started.instance().id(),detail.historySequence()).orElseThrow().detailsJson()).contains("assignment","GROUP","sandbox.finance");
        assertThat(jdbc.queryForObject("SELECT result_schema_version FROM wf.workflow_command_receipt WHERE command_id=?",Integer.class,command.commandId())).isEqualTo(5);
        var pending=start();
        denied(()->runtime.complete(command(pending,"APPROVE"),actor("f016-reviewer")),io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category.NOT_ASSIGNED);
        membership.removeTarget(target);
        assertThat(reads.findTask(pending.task().id()).orElseThrow().status()).isEqualTo(io.github.leontecristian.workflow.core.dto.runtime.TaskStatus.OPEN);
        denied(()->start(),io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category.INVALID_ASSIGNMENT);
        membership.replaceMembers(target,java.util.Set.of("f016-reviewer"));
        if(Boolean.getBoolean("f016.browserHold")) browserHold(pending, "f016");
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
                statement.execute("INSERT INTO #f016_task(id,activity_id,status,assignment_kind,assignment_ref) VALUES(103,203,'OPEN','GROUP',N'finance')");
                try (var input = getClass().getClassLoader().getResourceAsStream("db/changelog/workflow/changes/005-task-claim-release.xml")) {
                    var sql = factory.newDocumentBuilder().parse(input).getElementsByTagName("sql");
                    for (int i = 0; i < sql.getLength(); i++)
                        statement.execute(sql.item(i).getTextContent().replace("wf.workflow_task", "#f016_task"));
                }
                try (var rows = statement.executeQuery("SELECT id,assignment_ref,assignee_ref,closed_by FROM #f016_task ORDER BY id")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("assignment_ref")).isEqualTo("Alice ");
                    assertThat(rows.getString("assignee_ref")).isEqualTo("Alice ");
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("closed_by")).isEqualTo("Ștefan");
                    assertThat(rows.getString("assignee_ref")).isNull();
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("assignment_ref")).isEqualTo("finance");
                    assertThat(rows.getString("assignee_ref")).isNull();
                    assertThat(rows.next()).isFalse();
                }
                org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        statement.executeUpdate("UPDATE #f016_task SET assignee_ref=N'Alice' WHERE id=101"))
                        .isInstanceOf(java.sql.SQLException.class);
                org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        statement.executeUpdate("UPDATE #f016_task SET assignee_ref=NULL WHERE id=101"))
                        .isInstanceOf(java.sql.SQLException.class);
                statement.execute("DROP TABLE #f016_task");
            } catch(Exception failure) {throw new RuntimeException(failure);}
            return null;
        });
        prepare("sandbox.singleReviewer", "REQUIRED");
        var current=start();
        assertThat(current.task().assigneeRef()).isEqualTo("f016-reviewer");
        denied(() -> asActor("f016-reviewer", false, () -> runtime.claim(claim(current), actor("f016-reviewer"))), TASK_NOT_CLAIMABLE);
        denied(() -> asActor("f016-reviewer", false, () -> runtime.release(release(current), actor("f016-reviewer"))), TASK_NOT_CLAIMABLE);
        var adminRelease = new AdministrativelyReleaseWorkflowTask(UUID.randomUUID(), current.instance().id(),
                current.task().id(), current.instance().lockVersion(), "reason");
        denied(() -> asActor("f016-admin", true, () -> runtime.administrativelyRelease(adminRelease, actor("f016-admin"))), TASK_NOT_CLAIMABLE);
        var currentCreation=reads.listHistory(current.instance().id(),0,100).items().stream().filter(h->h.eventType().equals("TASK_CREATED")).findFirst().orElseThrow();
        String currentEventId=current.instance().id()+":"+currentCreation.historySequence();
        String currentJson=jdbc.queryForObject("SELECT event_json FROM wf_test.workflow_event_outbox WHERE event_id=?",String.class,currentEventId);
        assertThat(json.readTree(currentJson).path("eventSchemaVersion").asInt()).isEqualTo(3);
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
        var task=(ObjectNode)legacy.path("task");task.remove(List.of("assignment", "assigneeRef"));task.put("assignedUserRef","f016-reviewer");
        jdbc.update("UPDATE wf.workflow_command_receipt SET result_schema_version=3,result_json=? WHERE command_id=?",legacy.toString(),command.commandId());
        var replayed = runtime.replayComplete(command, actor("f016-reviewer")).orElseThrow().execution();
        assertThat(replayed.task().assigneeRef()).isNull();
        assertThat(replayed.task().closedBy()).isEqualTo(accepted.execution().task().closedBy());
        assertThat(replayed.instance()).isEqualTo(accepted.execution().instance());
        assertThat(jdbc.queryForObject("SELECT details_json FROM wf.workflow_history WHERE instance_id=? AND sequence_number=?",String.class,started.instance().id(),creation.historySequence())).isEqualTo(oldCreation);
    }
    private <T> T asActor(String actor, boolean admin, java.util.function.Supplier<T> action) {
        var holder = org.springframework.security.core.context.SecurityContextHolder.getContextHolderStrategy();
        var previous = holder.getContext();
        var authenticated = holder.createEmptyContext();
        authenticated.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(actor, "unused",
                List.of(new SimpleGrantedAuthority(admin ? "ROLE_CONSOLE_ADMIN" : "ROLE_CONSOLE_REVIEWER"))));
        holder.setContext(authenticated);
        try { return action.get(); }
        finally { holder.setContext(previous); }
    }

    private ClaimWorkflowTask claim(WorkflowExecutionResult snapshot) {
        return new ClaimWorkflowTask(UUID.randomUUID(), snapshot.instance().id(), snapshot.task().id(),
                snapshot.instance().lockVersion());
    }

    private ReleaseWorkflowTask release(WorkflowExecutionResult snapshot) {
        return new ReleaseWorkflowTask(UUID.randomUUID(), snapshot.instance().id(), snapshot.task().id(),
                snapshot.instance().lockVersion());
    }

    @Test void requiredOwnershipDiscoveryReleaseReplayAndRollback() throws Exception {
        prepare("sandbox.sharedGroupReviewer", "REQUIRED");
        String owner = "f016-reviewer", peer = "f016-reviewer2", admin = "f016-admin";
        membership.replaceMembers(target, java.util.Set.of(owner, peer));
        var opened = start();
        long instanceId = opened.instance().id(), taskId = opened.task().id();
        denied(() -> runtime.complete(command(opened, "APPROVE"), actor(owner)), CLAIM_REQUIRED);
        var claim = claim(opened);
        io.github.leontecristian.workflow.core.service.runtime.WorkflowEventRecorder recorderTarget = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(recorder);
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("test recorder failure");
        }).when(recorderTarget).record(org.mockito.ArgumentMatchers.argThat(batch ->
                batch.stream().anyMatch(event -> "TASK_CLAIMED".equals(event.eventType()))));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> asActor(owner, false, () -> runtime.claim(claim, actor(owner))))
                .isInstanceOf(RuntimeException.class);
        org.mockito.Mockito.reset(recorderTarget);
        assertThat(reads.findTask(taskId).orElseThrow().assigneeRef()).isNull();
        assertThat(reads.findInstance(instanceId).orElseThrow().lockVersion()).isEqualTo(opened.instance().lockVersion());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_history WHERE command_id=?", Integer.class, claim.commandId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_command_receipt WHERE command_id=?", Integer.class, claim.commandId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_test.workflow_event_outbox WHERE instance_id=? AND event_type='TASK_CLAIMED'", Integer.class, instanceId)).isZero();

        var claimed = asActor(owner, false, () -> runtime.claim(claim, actor(owner))).execution();
        assertThat(claimed.task().assigneeRef()).isEqualTo(owner);
        assertThat(claimed.task().assignment()).isEqualTo(opened.task().assignment());
        assertThat(claimed.firstHistorySequence()).isEqualTo(claimed.lastHistorySequence());
        denied(() -> asActor(owner, false, () -> runtime.claim(claim(claimed), actor(owner))), TASK_ALREADY_ASSIGNED);
        denied(() -> runtime.complete(command(claimed, "APPROVE"), actor(peer)), TASK_ASSIGNED_TO_OTHER);
        assertThat(reads.listEligibleTasks(peer, taskId - 1, 1, t -> t.id() == taskId).items())
                .extracting(t -> t.assigneeRef()).containsExactly(owner);

        membership.replaceMembers(target, java.util.Set.of(peer));
        denied(() -> runtime.complete(command(claimed, "APPROVE"), actor(owner)), NOT_ASSIGNED);
        var mvc = MockMvcBuilders.webAppContextSetup(web).addFilters(web.getBean(FilterChainProxy.class)).build();
        var reviewer = session(owner, "ROLE_CONSOLE_REVIEWER");
        var contract = mvc.perform(get("/console/api/tasks/" + taskId + "/contract").session(reviewer)).andReturn().getResponse();
        assertThat(contract.getStatus()).withFailMessage(contract.getContentAsString()).isEqualTo(200);
        assertThat(json.readTree(contract.getContentAsString()).path("canRelease").asBoolean()).isTrue();
        assertThat(json.readTree(contract.getContentAsString()).path("canComplete").asBoolean()).isFalse();
        assertThat(mvc.perform(get("/console/api/instances/" + instanceId + "/history").session(reviewer))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(reads.listEligibleTasks(owner, taskId - 1, 1, t -> t.id() == taskId).items())
                .extracting(t -> t.id()).containsExactly(taskId);
        assertThat(asActor(owner, false, () -> execution.findReplay(claim, actor(owner)))).contains(claimed);
        denied(() -> asActor(peer, false, () -> execution.findReplay(claim, actor(peer))), COMMAND_ID_CONFLICT);

        var release = release(claimed);
        var released = asActor(owner, false, () -> runtime.release(release, actor(owner))).execution();
        assertThat(released.task().assigneeRef()).isNull();
        assertThat(released.task().id()).isEqualTo(taskId);
        assertThat(released.task().assignment()).isEqualTo(target);
        denied(() -> asActor(owner, false, () -> runtime.release(release(released), actor(owner))), TASK_NOT_ASSIGNED);
        assertThat(mvc.perform(get("/console/api/tasks/" + taskId).session(reviewer))
                .andReturn().getResponse().getStatus()).isEqualTo(404);

        membership.replaceMembers(target, java.util.Set.of(owner, peer));
        var reclaimed = asActor(owner, false, () -> runtime.claim(claim(released), actor(owner))).execution();
        var stale = new ReleaseWorkflowTask(UUID.randomUUID(), instanceId, taskId, claimed.instance().lockVersion());
        denied(() -> asActor(owner, false, () -> runtime.release(stale, actor(owner))), STALE_VERSION);
        assertThat(asActor(owner, false, () -> execution.findReplay(release, actor(owner)))).contains(released);
        assertThat(reads.findTask(taskId).orElseThrow().assigneeRef()).isEqualTo(owner);

        String reason = "  Supervisor approved release  ";
        var adminRelease = new AdministrativelyReleaseWorkflowTask(UUID.randomUUID(), instanceId, taskId,
                reclaimed.instance().lockVersion(), reason);
        denied(() -> asActor(peer, false, () -> runtime.administrativelyRelease(adminRelease, actor(peer))), ADMIN_RELEASE_FORBIDDEN);
        var adminResult = asActor(admin, true, () -> runtime.administrativelyRelease(adminRelease, actor(admin))).execution();
        var detail = reads.findHistoryEvent(instanceId, adminResult.lastHistorySequence()).orElseThrow();
        assertThat(json.readTree(detail.detailsJson()).path("reason").asText()).isEqualTo(reason);
        var event = jdbc.queryForObject("SELECT event_json FROM wf_test.workflow_event_outbox WHERE event_id=?",
                String.class, instanceId + ":" + adminResult.lastHistorySequence());
        assertThat(json.readTree(event).path("payload").has("reason")).isFalse();
        assertThat(asActor(admin, false, () -> execution.findReplay(adminRelease, actor(admin)))).contains(adminResult);
        denied(() -> execution.findReplay(adminRelease, actor(admin)), NOT_FOUND);

        var finalClaim = asActor(peer, false, () -> runtime.claim(claim(adminResult), actor(peer))).execution();
        var completed = runtime.complete(command(finalClaim, "APPROVE"), actor(peer)).execution();
        assertThat(completed.task().assigneeRef()).isEqualTo(peer);
        assertThat(completed.task().closedBy()).isEqualTo(peer);
        if (Boolean.getBoolean("f017.browserHold")) browserHold(start(), "f017");
    }

    @Test void claimRacesAgainstClaimAndOptionalCompletion() throws Exception {
        prepare("sandbox.sharedGroupReviewer");
        String owner = "f016-reviewer", peer = "f016-reviewer2";
        membership.replaceMembers(target, java.util.Set.of(owner, peer));
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            for (boolean competeWithCompletion : List.of(false, true)) {
                var opened = start();
                var first = claim(opened);
                var second = claim(opened);
                var completion = command(opened, "APPROVE");
                var gate = new java.util.concurrent.CountDownLatch(1);
                var a = pool.submit(() -> concurrentResult(gate,
                        () -> asActor(owner, false, () -> runtime.claim(first, actor(owner)))));
                var b = pool.submit(() -> concurrentResult(gate, () -> competeWithCompletion
                        ? runtime.complete(completion, actor(peer))
                        : asActor(peer, false, () -> runtime.claim(second, actor(peer)))));
                gate.countDown();
                var results = List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
                assertThat(results).filteredOn(WorkflowCommandResult.class::isInstance).hasSize(1);
                assertThat(results).filteredOn(x -> x == STALE_VERSION || x == CONCURRENT_COMMAND).hasSize(1);
                var persisted = reads.findTask(opened.task().id()).orElseThrow();
                if (persisted.closedBy() == null) assertThat(persisted.assigneeRef()).isIn(owner, peer);
                else assertThat(persisted.closedBy()).isEqualTo(peer);
            }
        } finally { pool.shutdownNow(); }
    }

    private Object concurrentResult(java.util.concurrent.CountDownLatch gate,
            java.util.function.Supplier<WorkflowCommandResult> operation) throws InterruptedException {
        gate.await();
        try { return operation.get(); }
        catch (io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException failure) {
            return failure.category();
        }
    }

    private void browserHold(WorkflowExecutionResult pending, String feature) throws Exception {
        Path local=repository().resolve(".local");Files.createDirectories(local);
        Files.writeString(local.resolve(feature + "-runtime.json"),json.writeValueAsString(Map.of("port",port,"pid",ProcessHandle.current().pid(),
                "instance",pending.instance().id(),"task",pending.task().id(),"activity",pending.task().activityId(),"version",versionId)));
        Path done=local.resolve(feature + "-browser.done");long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(20);
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
