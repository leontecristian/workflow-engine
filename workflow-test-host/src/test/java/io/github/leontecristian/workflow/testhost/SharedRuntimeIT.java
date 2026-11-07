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
        "workflow.console.admin.username=f015-admin","workflow.console.admin.password=f015-local-test-password",
        "workflow.console.reviewer.username=f015-reviewer","workflow.console.reviewer.password=f015-local-test-password"})
@Import(SharedRuntimeIT.FailureFixture.class)
class SharedRuntimeIT extends DatabaseIntegrationTest {
    @Autowired WorkflowDefinitionService definitions;
    @Autowired WorkflowRuntimeQueryService reads;
    @Autowired TaskTypeCatalogService catalog;
    @Autowired SandboxRequestStore requests;
    @Autowired ConsoleRuntimeTransaction runtime;
    @Autowired AtomicBoolean failCompletion;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired WebApplicationContext web;
    @LocalServerPort int port;
    private final ObjectMapper json=new ObjectMapper();
    private final String marker="f015."+UUID.randomUUID();
    private final List<Long> instances=new ArrayList<>();
    private Long versionId,familyId,ownedTaskType;
    private TransactionTemplate tx() { return new TransactionTemplate(transactions); }
    private Path repository() { return Files.isDirectory(Path.of("docs"))?Path.of(""):Path.of(".."); }
    private WorkflowCommandContext context() { return new WorkflowCommandContext(ActorKind.USER,"f015-admin",Instant.now(),marker); }

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
    @Test void movedReadsCompletionReplayAndRollbackRetainHostSemantics() throws Exception {
        if(catalog.findByCode("SANDBOX_REVIEW").isEmpty()) ownedTaskType=tx().execute(t->catalog.create(new CreateTaskType("SANDBOX_REVIEW",marker,null,true)).after().id());
        var document=(ObjectNode)json.readTree(Files.readString(repository().resolve("docs/features/F006-test-host-definition-console/request-status-review.json")));
        document.put("key",marker);
        tx().executeWithoutResult(t->{
            var draft=definitions.importDraft(new ImportWorkflowDraft(document.toString()),new DefinitionManagementContext(marker,Instant.now())).after();
            versionId=draft.id();familyId=draft.definitionId();
            var published=definitions.publish(new CheckWorkflowDefinition(versionId,draft.lockVersion()),new DefinitionManagementContext(marker,Instant.now()));
            assertThat(published.published()).withFailMessage(()->published.report().toString()).isTrue();
        });
        var mvc=MockMvcBuilders.webAppContextSetup(web).addFilters(web.getBean(FilterChainProxy.class)).build();
        var reviewer=session("f015-reviewer","ROLE_CONSOLE_REVIEWER");
        var admin=session("f015-admin","ROLE_CONSOLE_ADMIN");
        var csrf=json.readTree(mvc.perform(get("/console/api/session").session(reviewer)).andReturn().getResponse().getContentAsString());
        var started=start();long instance=started.instance().id(),task=started.task().id();
        for(String url:List.of("/instances/"+instance+"/variables","/instances/"+instance+"/tasks","/tasks/"+task,
                "/tasks/"+task+"/contract","/tasks/"+task+"/submission","/activities/"+started.task().activityId(),
                "/activities/"+started.task().activityId()+"/submissions","/tasks/mine"))
            assertThat(mvc.perform(get("/console/api"+url).session(reviewer)).andReturn().getResponse().getStatus()).as(url).isEqualTo(200);
        var variables=mvc.perform(get("/console/api/instances/"+instance+"/variables").session(reviewer)).andReturn().getResponse().getContentAsString();
        assertThat(variables).contains("9007199254740993.00");
        assertThat(mvc.perform(get("/console/api/instances").session(reviewer)).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(get("/console/api/instances").session(admin)).andReturn().getResponse().getStatus()).isEqualTo(200);
        String endpoint="/console/api/instances/"+instance+"/tasks/"+task+"/complete";
        UUID command=UUID.randomUUID();
        assertThat(mvc.perform(post(endpoint).session(reviewer).contentType("application/json").header("X-Expected-Lock-Version",started.instance().lockVersion())
            .content(completion(started,command))).andReturn().getResponse().getStatus()).isEqualTo(403);
        var accepted=mvc.perform(post(endpoint).session(reviewer).contentType("application/json")
            .header(csrf.path("csrfHeaderName").asText(),csrf.path("csrfToken").asText())
            .header("X-Expected-Lock-Version",started.instance().lockVersion()).content(completion(started,command))).andReturn().getResponse();
        assertThat(accepted.getStatus()).withFailMessage(accepted.getContentAsString()).isEqualTo(200);
        assertThat(json.readTree(accepted.getContentAsString()).path("replayed").asBoolean()).isFalse();
        var replay=mvc.perform(post("/console/api/commands/complete/replay").session(reviewer).contentType("application/json")
            .header(csrf.path("csrfHeaderName").asText(),csrf.path("csrfToken").asText()).header("X-Expected-Lock-Version",started.instance().lockVersion())
            .content(replayBody(started,command))).andReturn().getResponse();
        assertThat(replay.getStatus()).withFailMessage(replay.getContentAsString()).isEqualTo(200);
        assertThat(json.readTree(replay.getContentAsString()).path("found").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_history WHERE instance_id=? AND event_type='TASK_COMPLETED'",Integer.class,instance)).isEqualTo(1);
        var pending=start();long before=pending.instance().lockVersion();
        var missing=mvc.perform(post("/console/api/commands/complete/replay").session(reviewer).contentType("application/json")
            .header(csrf.path("csrfHeaderName").asText(),csrf.path("csrfToken").asText()).header("X-Expected-Lock-Version",before)
            .content(replayBody(pending,UUID.randomUUID()))).andReturn().getResponse();
        assertThat(missing.getStatus()).isEqualTo(200);assertThat(json.readTree(missing.getContentAsString()).path("found").asBoolean()).isFalse();
        assertThat(reads.findInstance(pending.instance().id()).orElseThrow().lockVersion()).isEqualTo(before);
        failCompletion.set(true);
        try {
            var failed=mvc.perform(post("/console/api/instances/"+pending.instance().id()+"/tasks/"+pending.task().id()+"/complete")
                .session(reviewer).contentType("application/json").header(csrf.path("csrfHeaderName").asText(),csrf.path("csrfToken").asText())
                .header("X-Expected-Lock-Version",before).content(completion(pending,UUID.randomUUID()))).andReturn().getResponse();
            assertThat(failed.getStatus()).isEqualTo(500);assertThat(failed.getContentAsString()).doesNotContain("Configured");
        } finally { failCompletion.set(false); }
        assertThat(reads.findInstance(pending.instance().id()).orElseThrow().lockVersion()).isEqualTo(before);
        assertThat(reads.findTaskSubmission(pending.task().id())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_command_receipt WHERE instance_id=? AND operation_type='COMPLETE_TASK'",Integer.class,pending.instance().id())).isZero();
        if(Boolean.getBoolean("f015.browserHold")) {
            Path local=repository().resolve(".local");Files.createDirectories(local);
            Files.writeString(local.resolve("f015-runtime.json"),json.writeValueAsString(Map.of("port",port,"pid",ProcessHandle.current().pid(),
                "instance",pending.instance().id(),"task",pending.task().id(),"activity",pending.task().activityId(),"version",versionId)));
            Path done=local.resolve("f015-browser.done");long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(20);
            while(!Files.exists(done)&&System.nanoTime()<deadline) Thread.sleep(250);
            assertThat(Files.exists(done)).as("Focused browser completion signal").isTrue();Files.deleteIfExists(done);
        }
    }
    @AfterEach void cleanup() {
        failCompletion.set(false);
        tx().executeWithoutResult(t->{
            for(long id:instances) {
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
