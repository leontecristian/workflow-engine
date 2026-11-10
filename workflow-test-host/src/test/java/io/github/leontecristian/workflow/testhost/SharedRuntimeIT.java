package io.github.leontecristian.workflow.testhost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leontecristian.workflow.core.dto.runtime.ActionField;
import io.github.leontecristian.workflow.core.dto.definition.CheckWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionManagementContext;
import io.github.leontecristian.workflow.core.dto.definition.ImportWorkflowDraft;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.NullLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionContext;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionDescriptor;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionResult;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.TextLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowExecutionResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistoryEvent;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import io.github.leontecristian.workflow.core.dto.tasktype.CreateTaskType;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowExecutionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowServiceAction;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.ObjectProvider;
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
        "workflow.console.fail-service-node=failRecordAmount",
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
    private String ownedDefinitionKey;
    private TransactionTemplate tx() { return new TransactionTemplate(transactions); }
    private Path repository() { return Files.isDirectory(Path.of("docs"))?Path.of(""):Path.of(".."); }
    private WorkflowCommandContext context() { return new WorkflowCommandContext(ActorKind.USER,"f015-admin",Instant.now(),marker); }

    /** Test-only selection of the existing post-result failure under the real transaction proxy.
     * Start and ordinary completion still call the production transaction body unchanged.
     */
    @TestConfiguration(proxyBeanMethods=false)
    static class FailureFixture {
        @Bean AtomicBoolean failCompletion() { return new AtomicBoolean(); }

        @Bean WorkflowServiceAction replaceReviewAmount(ObjectProvider<WorkflowRuntimeQueryService> reads) {
            return new WorkflowServiceAction() {
                @Override public ServiceActionDescriptor descriptor() {
                    var field = new ActionField("reviewResult", VariableValueType.JSON, true, false);
                    return new ServiceActionDescriptor("test.replaceReviewAmount", List.of(field), List.of(field));
                }

                @Override public ServiceActionResult execute(ServiceActionContext context, Map<String, WorkflowValue> inputs) {
                    // The source resolves the current completion, and a query in the same transaction sees its copy.
                    var page = reads.getObject().listVariables(context.instanceId(), null, 50, null);
                    assertThat(page.items()).filteredOn(variable -> variable.key().equals("reviewResult")).singleElement()
                            .satisfies(variable -> assertThat(variable.value()).isEqualTo(inputs.get("reviewResult")));
                    return new ServiceActionResult(Map.of("reviewResult", new WorkflowValue(VariableValueType.JSON,
                            WorkflowJsonValue.parse("{\"approvedAmount\":900.0}"))));
                }
            };
        }
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
        ownedDefinitionKey = marker;
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
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void structuredSubmissionAndSandboxEffectShareTheCompletionTransaction(boolean failDownstream) throws Exception {
        prepareStructuredDefinition(failDownstream);
        var started = startStructured();
        long instanceId = started.instance().id();
        var reviewer = new WorkflowCommandContext(ActorKind.USER, "f015-reviewer", Instant.now(), marker);
        var accepted = CompleteWorkflowTask.parseResult("{\"approvedAmount\":1250.00,\"note\":\"Approved\"}");
        var first = new CompleteWorkflowTask(UUID.randomUUID(), instanceId, started.task().id(),
                started.instance().lockVersion(), "COMPLETE", null, accepted);
        if (!failDownstream) {
            for (var invalid : Map.of(
                    "{\"approvedAmount\":1}", Category.RESULT_SCHEMA_INVALID,
                    "{\"approvedAmount\":10000.01,\"note\":\"Over limit\"}", Category.RESULT_REJECTED,
                    "{\"approvedAmount\":1.001,\"note\":\"Fractional cent\"}", Category.RESULT_REJECTED).entrySet()) {
                var rejected = new CompleteWorkflowTask(UUID.randomUUID(), instanceId, first.taskId(), first.expectedLockVersion(),
                        "COMPLETE", null, CompleteWorkflowTask.parseResult(invalid.getKey()));
                assertThatThrownBy(() -> runtime.complete(rejected, reviewer)).isInstanceOfSatisfying(
                        WorkflowExecutionException.class, e -> assertThat(e.category()).isEqualTo(invalid.getValue()));
                assertThat(runtime.replayComplete(rejected, reviewer)).isEmpty();
                assertThat(reads.findTaskSubmission(first.taskId())).isEmpty();
                assertThat(reads.findInstance(instanceId).orElseThrow().lockVersion()).isEqualTo(first.expectedLockVersion());
            }
        }
        runtime.complete(first, reviewer);
        assertThat(reads.findTaskSubmission(first.taskId()).orElseThrow().result()).isEqualTo(accepted);
        assertThat(reads.listActivitySubmissions(started.activity().id()).items()).singleElement()
                .satisfies(submission -> assertThat(submission.result()).isEqualTo(accepted));

        if (failDownstream) {
            var current = reads.listTasks(instanceId).items().stream().filter(task -> task.id() != first.taskId()).findFirst().orElseThrow();
            long token = reads.findInstance(instanceId).orElseThrow().lockVersion();
            var failing = new CompleteWorkflowTask(UUID.randomUUID(), instanceId, current.id(), token, "COMPLETE", null, accepted);
            assertThatThrownBy(() -> runtime.complete(failing, reviewer)).isInstanceOfSatisfying(
                    WorkflowExecutionException.class, e -> {
                        assertThat(e.category()).isEqualTo(Category.SERVICE_ACTION_FAILED);
                        assertThat(e).hasRootCauseMessage("Configured service failure");
                    });
            assertThat(runtime.replayComplete(failing, reviewer)).isEmpty();
            assertThat(reads.findTaskSubmission(current.id())).isEmpty();
            assertThat(reads.findTaskSubmission(first.taskId()).orElseThrow().result()).isEqualTo(accepted);
            assertThat(reads.findInstance(instanceId).orElseThrow().lockVersion()).isEqualTo(token);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_test.service_effect WHERE instance_id=?", Integer.class, instanceId))
                    .isZero();
        } else {
            var reordered = new CompleteWorkflowTask(first.commandId(), instanceId, first.taskId(), first.expectedLockVersion(),
                    "COMPLETE", null, CompleteWorkflowTask.parseResult("{\"note\":\"Approved\",\"approvedAmount\":1250.00}"));
            assertThat(runtime.replayComplete(reordered, reviewer)).isPresent();
            var changed = new CompleteWorkflowTask(first.commandId(), instanceId, first.taskId(), first.expectedLockVersion(),
                    "COMPLETE", null, CompleteWorkflowTask.parseResult("{\"approvedAmount\":1250,\"note\":\"Approved\"}"));
            assertThatThrownBy(() -> runtime.replayComplete(changed, reviewer)).isInstanceOfSatisfying(
                    WorkflowExecutionException.class, e -> assertThat(e.category()).isEqualTo(Category.COMMAND_ID_CONFLICT));
            assertThat(jdbc.queryForList("SELECT quantity FROM wf_test.service_effect WHERE instance_id=?", Long.class, instanceId))
                    .containsExactly(125000L);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void namedOutputAndSandboxEffectShareTheCompletionTransaction(boolean failDownstream) throws Exception {
        var document = namedOutputDocument();
        if (failDownstream) {
            ((ObjectNode) document.path("nodes").get(2)).put("key", "failRecordAmount");
            ((ObjectNode) document.path("transitions").get(1)).put("to", "failRecordAmount");
            ((ObjectNode) document.path("transitions").get(2)).put("from", "failRecordAmount");
        }
        publishStructuredDefinition(document);
        var started = startStructured();
        long instanceId = started.instance().id();
        var reviewer = new WorkflowCommandContext(ActorKind.USER, "f015-reviewer", Instant.now(), marker);
        var result = CompleteWorkflowTask.parseResult("{\"approvedAmount\":1250.00,\"note\":\"Approved\"}");
        var command = new CompleteWorkflowTask(UUID.randomUUID(), instanceId, started.task().id(),
                started.instance().lockVersion(), "COMPLETE", null, result);
        assertThat(reads.listVariables(instanceId, null, 50, null).items()).isEmpty();

        if (failDownstream) {
            assertThatThrownBy(() -> runtime.complete(command, reviewer)).isInstanceOfSatisfying(
                    WorkflowExecutionException.class, e -> {
                        assertThat(e.category()).isEqualTo(Category.SERVICE_ACTION_FAILED);
                        assertThat(e).hasRootCauseMessage("Configured service failure");
                    });
            assertThat(reads.listVariables(instanceId, null, 50, null).items()).isEmpty();
            assertThat(reads.findTaskSubmission(started.task().id())).isEmpty();
            assertThat(runtime.replayComplete(command, reviewer)).isEmpty();
            assertThat(reads.findInstance(instanceId).orElseThrow().lockVersion()).isEqualTo(command.expectedLockVersion());
            assertThat(effectQuantities(instanceId)).isEmpty();
            assertThat(reads.listHistory(instanceId).items()).noneMatch(item -> item.commandId().equals(command.commandId()));
            return;
        }

        runtime.complete(command, reviewer);
        assertThat(runtime.replayComplete(command, reviewer)).isPresent();
        assertThat(reads.findTaskSubmission(started.task().id()).orElseThrow().result()).isEqualTo(result);
        assertThat(reads.listVariables(instanceId, null, 50, null).items()).singleElement().satisfies(variable -> {
            assertThat(variable.key()).isEqualTo("reviewResult");
            assertThat(variable.value().value()).isEqualTo(result);
        });
        assertThat(effectQuantities(instanceId)).containsExactly(125000L);

        var writes = reads.listHistory(instanceId).items().stream()
                .filter(item -> item.eventType().equals("VARIABLES_WRITTEN")).toList();
        assertThat(writes).hasSize(1);
        var write = writes.get(0);
        assertThat(write.taskId()).isEqualTo(started.task().id());
        assertThat(write.activityId()).isEqualTo(started.activity().id());
        assertThat(write.commandId()).isEqualTo(command.commandId());
        String details = reads.findHistoryEvent(instanceId, write.historySequence()).orElseThrow().detailsJson();
        assertThat(details).contains(result.toJson());
        var decoded = json.readTree(details);
        assertThat(decoded.path("nodeKey").asText()).isEqualTo("reviewAmount");
        var entry = decoded.path("writes").get(0);
        assertThat(entry.path("previousInitialized").booleanValue()).isFalse();
        assertThat(entry.path("source").path("source").asText()).isEqualTo("taskResult");
        assertThat(entry.path("source").path("key").asText()).isEqualTo("reviewAmount");
        var service = serviceEvidence(instanceId, "recordAmount");
        assertThat(service.detailsJson()).contains(result.toJson());
        assertThat(jdbc.queryForObject("""
                SELECT event_json FROM wf_test.workflow_event_outbox WHERE instance_id=? AND event_type='VARIABLES_WRITTEN'
                """, String.class, instanceId)).doesNotContain("approvedAmount");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void submissionSourceSelectsFinanceAfterManagerCompletion(boolean omitManagerResult) throws Exception {
        var document = financeThenManagerDocument();
        publishStructuredDefinition(document);
        var started = startStructured();
        long instanceId = started.instance().id();
        var reviewer = new WorkflowCommandContext(ActorKind.USER, "f015-reviewer", Instant.now(), marker);
        var original = CompleteWorkflowTask.parseResult("{\"approvedAmount\":1000.00,\"note\":\"Finance\"}");
        var finance = new CompleteWorkflowTask(UUID.randomUUID(), instanceId, started.task().id(),
                started.instance().lockVersion(), "COMPLETE", null, original);
        runtime.complete(finance, reviewer);
        var managerTask = reads.listTasks(instanceId).items().stream()
                .filter(task -> task.id() != finance.taskId()).findFirst().orElseThrow();
        long token = reads.findInstance(instanceId).orElseThrow().lockVersion();
        var managerResult = CompleteWorkflowTask.parseResult("{\"approvedAmount\":800.000,\"note\":\"Manager\"}");
        var manager = new CompleteWorkflowTask(UUID.randomUUID(), instanceId, managerTask.id(), token,
                "COMPLETE", null, omitManagerResult ? null : managerResult);

        if (omitManagerResult) {
            assertThatThrownBy(() -> runtime.complete(manager, reviewer)).isInstanceOfSatisfying(
                    WorkflowExecutionException.class, e -> assertThat(e.category()).isEqualTo(Category.SUBMISSION_UNAVAILABLE));
            assertThat(runtime.replayComplete(manager, reviewer)).isEmpty();
            assertThat(reads.findTaskSubmission(manager.taskId())).isEmpty();
            assertThat(reads.findInstance(instanceId).orElseThrow().lockVersion()).isEqualTo(token);
            assertThat(reads.listHistory(instanceId).items()).noneMatch(item -> item.commandId().equals(manager.commandId()));
            assertThat(effectQuantities(instanceId)).isEmpty();
        } else {
            runtime.complete(manager, reviewer);
            assertThat(runtime.replayComplete(manager, reviewer)).isPresent();
            assertThat(effectQuantities(instanceId)).containsExactly(90000L, 100000L, 80000L);
            assertThat(serviceEvidence(instanceId, "recordWorking").detailsJson()).contains("{\"approvedAmount\":900.0}");
            var financeEvent = serviceEvidence(instanceId, "recordFinance");
            assertThat(financeEvent.detailsJson()).contains(original.toJson());
            assertThat(financeEvent.summary().commandId()).isEqualTo(manager.commandId());
            assertThat(serviceEvidence(instanceId, "recordManager").detailsJson()).contains(managerResult.toJson());
            long sequence = financeEvent.summary().historySequence();
            var mvc = MockMvcBuilders.webAppContextSetup(web).addFilters(web.getBean(FilterChainProxy.class)).build();
            String path = "/console/api/instances/" + instanceId + "/history/" + sequence;
            var allowed = mvc.perform(get(path).session(session("f015-reviewer", "ROLE_CONSOLE_REVIEWER"))).andReturn().getResponse();
            assertThat(allowed.getStatus()).isEqualTo(200);
            assertThat(json.readTree(allowed.getContentAsString()).path("detailsJson").asText()).isEqualTo(financeEvent.detailsJson());
            var denied = mvc.perform(get(path).session(session("unassigned", "ROLE_CONSOLE_REVIEWER"))).andReturn().getResponse();
            assertThat(denied.getStatus()).isEqualTo(404);
            assertThat(denied.getContentAsString()).doesNotContain("approvedAmount");
        }
        assertThat(reads.findTaskSubmission(finance.taskId()).orElseThrow().result()).isEqualTo(original);
        assertThat(reads.listVariables(instanceId, null, 50, null).items()).singleElement().satisfies(variable ->
                assertThat(variable.value().value()).isEqualTo(WorkflowJsonValue.parse("{\"approvedAmount\":900.0}")));
    }

    private enum EntryFailure { MISSING_RESULT, UNINITIALIZED, LIMIT }

    @Test
    void humanTaskInputsPersistExactValuesAcrossReplayClaimAndCompletion() throws Exception {
        var entered = enterHumanTaskWithInputs();
        long instanceId = entered.started().instance().id();
        long sourceTaskId = entered.started().task().id();
        long taskId = entered.taskId();
        var inputs = reads.taskInputs(instanceId, taskId);
        assertThat(reads.taskInputs(instanceId, sourceTaskId)).isEmpty();
        assertThat(inputs).containsExactlyInAnyOrderEntriesOf(Map.of(
                "original", new WorkflowValue(VariableValueType.JSON, entered.command().result()),
                "current", new WorkflowValue(VariableValueType.JSON, WorkflowJsonValue.parse("{\"approvedAmount\":900.0}")),
                "reviewDate", new WorkflowValue(VariableValueType.DATE, new TextLiteral("2026-11-10")),
                "optionalNote", new WorkflowValue(VariableValueType.STRING, NullLiteral.INSTANCE)));

        var creation = reads.listHistory(instanceId).items().stream()
                .filter(item -> "TASK_CREATED".equals(item.eventType()) && Long.valueOf(taskId).equals(item.taskId()))
                .findFirst().orElseThrow();
        assertThat(creation.detailsSchemaVersion()).isEqualTo((short) 5);
        assertThat(reads.findHistoryEvent(instanceId, creation.historySequence())).isEmpty();
        assertThat(jdbc.queryForObject("""
                SELECT event_json FROM wf_test.workflow_event_outbox WHERE instance_id=? AND history_sequence=?
                """, String.class, instanceId, creation.historySequence())).doesNotContain("approvedAmount", "optionalNote");

        // The sandbox has no replacement endpoint. Change only this test-owned source row
        // to check that reloads read the snapshot independently of the variable store.
        assertThat(jdbc.update("""
                UPDATE wf.workflow_variable SET json_value=? WHERE instance_id=? AND variable_key='reviewResult'
                """, "{\"approvedAmount\":12.3}", instanceId)).isEqualTo(1);
        var replay = runtime.complete(entered.command(), reviewerContext("f015-reviewer"));
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.execution()).isEqualTo(entered.progressed());
        assertThat(reads.listHistory(instanceId).items()).filteredOn(item ->
                "TASK_CREATED".equals(item.eventType()) && item.detailsSchemaVersion() == 5).containsExactly(creation);
        assertThat(reads.taskInputs(instanceId, taskId)).isEqualTo(inputs);

        var mvc = MockMvcBuilders.webAppContextSetup(web).addFilters(web.getBean(FilterChainProxy.class)).build();
        var originalReviewer = session("f015-reviewer", "ROLE_CONSOLE_REVIEWER");
        var peer = session("f015-reviewer2", "ROLE_CONSOLE_REVIEWER");
        String taskContract = "/console/api/tasks/" + taskId + "/contract";
        String instance = "/console/api/instances/" + instanceId;
        String event = instance + "/history/" + creation.historySequence();
        var csrf = json.readTree(mvc.perform(get("/console/api/session").session(peer))
                .andReturn().getResponse().getContentAsString());
        var claimed = mvc.perform(post(instance + "/tasks/" + taskId + "/claim").session(peer)
                .contentType("application/json")
                .header(csrf.path("csrfHeaderName").asText(), csrf.path("csrfToken").asText())
                .header("X-Expected-Lock-Version", entered.progressed().instance().lockVersion())
                .content(json.createObjectNode().put("commandId", UUID.randomUUID().toString()).toString()))
                .andReturn().getResponse();
        assertThat(claimed.getStatus()).withFailMessage(claimed.getContentAsString()).isEqualTo(200);
        assertThat(reads.findTask(taskId).orElseThrow().assigneeRef()).isEqualTo("f015-reviewer2");
        assertThat(reads.taskInputs(instanceId, taskId)).isEqualTo(inputs);
        assertThat(mvc.perform(get("/console/api/tasks/" + sourceTaskId + "/submission").session(peer))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        var open = mvc.perform(get(taskContract).session(originalReviewer)).andReturn().getResponse();
        assertThat(open.getStatus()).isEqualTo(200);
        assertThat(open.getContentAsString()).contains("9007199254740993.00", "2026-11-10");
        var historyResponse = mvc.perform(get(event).session(peer)).andReturn().getResponse();
        assertThat(historyResponse.getStatus()).isEqualTo(200);
        assertThat(json.readTree(historyResponse.getContentAsString()).path("detailsJson").asText())
                .contains("9007199254740993.00");

        var answer = CompleteWorkflowTask.parseResult("{\"approvedAmount\":1250.00,\"note\":\"Receiver answer\"}");
        var complete = new CompleteWorkflowTask(UUID.randomUUID(), instanceId, taskId, reads.findInstance(instanceId).orElseThrow().lockVersion(),
                "COMPLETE", null, answer);
        runtime.complete(complete, reviewerContext("f015-reviewer2"));
        assertThat(reads.findTaskSubmission(taskId).orElseThrow().result()).isEqualTo(answer);
        assertThat(reads.taskInputs(instanceId, taskId)).isEqualTo(inputs);
        var completed = mvc.perform(get(taskContract).session(peer)).andReturn().getResponse();
        assertThat(completed.getStatus()).isEqualTo(200);
        assertThat(completed.getContentAsString()).contains("9007199254740993.00");
        assertThat(mvc.perform(get(instance).session(originalReviewer)).andReturn().getResponse().getStatus()).isEqualTo(200);
        var denied = mvc.perform(get(event).session(originalReviewer)).andReturn().getResponse();
        assertThat(denied.getStatus()).isEqualTo(404);
        assertThat(denied.getContentAsString()).doesNotContain("approvedAmount");
        assertThat(mvc.perform(get(taskContract).session(originalReviewer)).andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @ParameterizedTest
    @EnumSource(EntryFailure.class)
    void humanTaskInputsRollBackEnteringCompletion(EntryFailure failure) throws Exception {
        publishStructuredDefinition(humanTaskInputsDocument(failure));
        var started = startStructured();
        long instanceId = started.instance().id();
        var reviewer = reviewerContext("f015-reviewer");
        var result = failure == EntryFailure.MISSING_RESULT ? null : inputSourceResult();
        var command = new CompleteWorkflowTask(UUID.randomUUID(), instanceId, started.task().id(),
                started.instance().lockVersion(), "COMPLETE", null, result);
        var expected = switch (failure) {
            case MISSING_RESULT -> Category.SUBMISSION_UNAVAILABLE;
            case UNINITIALIZED -> Category.UNINITIALIZED_VARIABLE;
            case LIMIT -> Category.LIMIT_EXCEEDED;
        };
        assertThatThrownBy(() -> runtime.complete(command, reviewer)).isInstanceOfSatisfying(
                WorkflowExecutionException.class, error -> assertThat(error.category()).isEqualTo(expected));
        assertThat(reads.findInstance(instanceId).orElseThrow()).isEqualTo(started.instance());
        assertThat(reads.listTasks(instanceId).items()).containsExactly(started.task());
        assertThat(reads.findTaskSubmission(command.taskId())).isEmpty();
        assertThat(runtime.replayComplete(command, reviewer)).isEmpty();
        assertThat(reads.listVariables(instanceId, null, 50, null).items()).noneMatch(item -> item.key().equals("reviewResult"));
        assertThat(reads.listHistory(instanceId).items()).noneMatch(item -> item.commandId().equals(command.commandId()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_test.workflow_event_outbox WHERE instance_id=?",
                Long.class, instanceId)).isEqualTo(started.lastHistorySequence());
    }

    private record EnteredInputTask(WorkflowExecutionResult started, CompleteWorkflowTask command,
            WorkflowExecutionResult progressed, long taskId) { }

    private EnteredInputTask enterHumanTaskWithInputs() throws Exception {
        publishStructuredDefinition(humanTaskInputsDocument(null));
        var started = startStructured();
        long instanceId = started.instance().id();
        var command = new CompleteWorkflowTask(UUID.randomUUID(), instanceId, started.task().id(),
                started.instance().lockVersion(), "COMPLETE", null, inputSourceResult());
        var progressed = runtime.complete(command, reviewerContext("f015-reviewer")).execution();
        long taskId = reads.listTasks(instanceId).items().stream().filter(task -> task.status() == TaskStatus.OPEN)
                .findFirst().orElseThrow().id();
        return new EnteredInputTask(started, command, progressed, taskId);
    }

    private WorkflowCommandContext reviewerContext(String actor) {
        return new WorkflowCommandContext(ActorKind.USER, actor, Instant.now(), marker);
    }

    private WorkflowJsonValue inputSourceResult() {
        return CompleteWorkflowTask.parseResult("{\"approvedAmount\":9007199254740993.00,\"note\":\"Original answer\"}");
    }

    private ObjectNode humanTaskInputsDocument(EntryFailure failure) throws Exception {
        var document = namedOutputDocument().put("key", marker);
        var nodes = document.withArray("nodes");
        var source = (ObjectNode) nodes.get(1);
        var receiving = source.deepCopy().put("key", "receivingReview").put("name", "Review saved inputs");
        receiving.remove("outputVariable");
        receiving.put("claimPolicy", "REQUIRED");
        ((ObjectNode) receiving.get("assignment")).put("resolver", "sandbox.sharedGroupReviewer");
        var inputs = receiving.putObject("inputs");
        inputs.putObject("original").put("source", "submission").put("nodeKey", "reviewAmount");
        inputs.putObject("current").put("source", "variable").put("key", "reviewResult");
        inputs.putObject("reviewDate").put("source", "variable").put("key", "reviewDate");
        inputs.putObject("optionalNote").put("source", "variable").put("key", "optionalNote");
        nodes.add(receiving);

        var variables = document.withArray("variables");
        var initialize = (ObjectNode) document.path("startPolicy").path("initialize");
        variables.addObject().put("key", "reviewDate").put("type", "DATE").put("nullable", false);
        initialize.putObject("reviewDate").put("source", "literal").put("value", "2026-11-10");
        variables.addObject().put("key", "optionalNote").put("type", "STRING").put("nullable", true);
        if (failure != EntryFailure.UNINITIALIZED) initialize.putObject("optionalNote").put("source", "literal").putNull("value");
        if (failure == EntryFailure.LIMIT) {
            variables.addObject().put("key", "largeText").put("type", "STRING").put("nullable", false);
            initialize.putObject("largeText").put("source", "literal").put("value", "x".repeat(4000));
            for (int i = 0; i < 20; i++) inputs.putObject("copy" + i).put("source", "variable").put("key", "largeText");
        }

        var edges = document.withArray("transitions");
        edges.addObject().put("key", "answer").put("from", "receivingReview").put("to", "DONE").put("outcome", "COMPLETE");
        if (failure == EntryFailure.MISSING_RESULT) {
            ((ObjectNode) source.get("resultContract")).put("mode", "OPTIONAL");
            source.remove("outputVariable");
            inputs.remove("current");
            nodes.remove(2);
            ((ObjectNode) edges.get(1)).put("to", "receivingReview");
            edges.remove(2);
        } else {
            var replace = ((ObjectNode) nodes.get(2)).put("action", "test.replaceReviewAmount");
            ((ObjectNode) replace.get("outputs")).putObject("reviewResult").put("source", "actionOutput").put("key", "reviewResult");
            ((ObjectNode) edges.get(2)).put("to", "receivingReview");
        }
        return document;
    }

    private ObjectNode namedOutputDocument() throws Exception {
        return (ObjectNode) json.readTree(Files.readString(repository().resolve(
                "docs/features/F019-named-task-output-variables/sandbox-named-task-output.json")));
    }

    private ObjectNode financeThenManagerDocument() throws Exception {
        var document = namedOutputDocument();
        document.put("key", marker);
        var nodes = document.withArray("nodes");
        var finance = ((ObjectNode) nodes.get(1)).put("key", "financeReview");
        var manager = finance.deepCopy().put("key", "managerReview");
        manager.remove("outputVariable");
        ((ObjectNode) manager.get("resultContract")).put("mode", "OPTIONAL");
        nodes.add(manager);

        var working = ((ObjectNode) nodes.get(2)).put("key", "recordWorking");
        var originalFinance = working.deepCopy().put("key", "recordFinance");
        binding(originalFinance).remove("key");
        binding(originalFinance).put("source", "submission").put("nodeKey", "financeReview");
        var originalManager = originalFinance.deepCopy().put("key", "recordManager");
        binding(originalManager).put("nodeKey", "managerReview");
        nodes.add(originalFinance).add(originalManager);
        var replace = originalFinance.deepCopy().put("key", "replaceResult").put("action", "test.replaceReviewAmount");
        ((ObjectNode) replace.get("outputs")).putObject("reviewResult").put("source", "actionOutput").put("key", "reviewResult");
        nodes.add(replace);

        var edges = document.withArray("transitions").removeAll();
        edges.addObject().put("key", "begin").put("from", "BEGIN").put("to", "financeReview");
        edges.addObject().put("key", "finance").put("from", "financeReview").put("to", "replaceResult").put("outcome", "COMPLETE");
        edges.addObject().put("key", "replaced").put("from", "replaceResult").put("to", "managerReview");
        edges.addObject().put("key", "manager").put("from", "managerReview").put("to", "recordWorking").put("outcome", "COMPLETE");
        edges.addObject().put("key", "working").put("from", "recordWorking").put("to", "recordFinance");
        edges.addObject().put("key", "original").put("from", "recordFinance").put("to", "recordManager");
        edges.addObject().put("key", "done").put("from", "recordManager").put("to", "DONE");
        return document;
    }

    private ObjectNode binding(ObjectNode service) {
        return (ObjectNode) service.path("inputs").path("reviewResult");
    }

    private List<Long> effectQuantities(long instanceId) {
        return jdbc.queryForList("SELECT quantity FROM wf_test.service_effect WHERE instance_id=? ORDER BY id", Long.class, instanceId);
    }

    private WorkflowHistoryEvent serviceEvidence(long instanceId, String nodeKey) {
        return reads.listHistory(instanceId).items().stream().filter(item -> item.eventType().equals("SERVICE_TASK_COMPLETED"))
                .map(item -> reads.findHistoryEvent(instanceId, item.historySequence()).orElseThrow())
                .filter(event -> event.detailsJson().contains("\"nodeKey\":\"" + nodeKey + "\"")).findFirst().orElseThrow();
    }

    private void prepareStructuredDefinition(boolean failDownstream) throws Exception {

        var document = (ObjectNode) json.readTree(Files.readString(repository().resolve(
                "docs/features/F018-structured-task-submissions/sandbox-structured-review.json")));

        if (failDownstream) {
            var nodes = (com.fasterxml.jackson.databind.node.ArrayNode) document.get("nodes");
            var firstReview = ((ObjectNode) nodes.get(1)).deepCopy().put("key", "priorReview");
            nodes.add(firstReview);
            ((ObjectNode) nodes.get(2)).put("key", "failRecordAmount");
            var edges = (com.fasterxml.jackson.databind.node.ArrayNode) document.get("transitions");
            ((ObjectNode) edges.get(0)).put("to", "priorReview");
            ((ObjectNode) edges.get(1)).put("to", "failRecordAmount");
            ((ObjectNode) edges.get(2)).put("from", "failRecordAmount");
            edges.addObject().put("key", "reviewAgain").put("from", "priorReview").put("to", "reviewAmount").put("outcome", "COMPLETE");
        }
        publishStructuredDefinition(document);
    }

    private void publishStructuredDefinition(ObjectNode document) {
        if (catalog.findByCode("SANDBOX_REVIEW").isEmpty()) {
            ownedTaskType = tx().execute(t -> catalog.create(new CreateTaskType("SANDBOX_REVIEW", marker, null, true)).after().id());
        }
        String key = document.path("key").asText();
        var existing = jdbc.queryForList("SELECT id FROM wf.workflow_definition WHERE workflow_key=?", Long.class, key);
        if (!existing.isEmpty()) {
            int version = jdbc.queryForObject("SELECT COALESCE(MAX(version),0) FROM wf.workflow_definition_version WHERE definition_id=?",
                    Integer.class, existing.get(0));
            document.put("version", version + 1);
        } else {
            ownedDefinitionKey = key;
        }
        tx().executeWithoutResult(t -> {
            var draft = definitions.importDraft(new ImportWorkflowDraft(document.toString()),
                    new DefinitionManagementContext(marker, Instant.now())).after();
            versionId = draft.id();
            if (ownedDefinitionKey != null) familyId = draft.definitionId();
            var published = definitions.publish(new CheckWorkflowDefinition(versionId, draft.lockVersion()),
                    new DefinitionManagementContext(marker, Instant.now()));
            assertThat(published.published()).withFailMessage(() -> published.report().toString()).isTrue();
        });
    }

    private WorkflowExecutionResult startStructured() {
        long request = tx().execute(t -> requests.create(marker, marker, Instant.now()).id());
        var started = runtime.start(new SandboxStart(UUID.randomUUID(), versionId, request, 0), context()).execution();
        instances.add(started.instance().id());
        return started;
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "f018.browserHold", matches = "true")
    void liveStructuredSubmissionBrowserFixture() throws Exception {
        prepareStructuredDefinition(false);
        var started = startStructured();
        Path local = repository().resolve(".local");
        Files.createDirectories(local);
        Path state = local.resolve("f018-browser-fixture.json");
        Path done = local.resolve("f018-browser.done");
        try {
            Files.writeString(state, json.writeValueAsString(Map.of("port", port, "pid", ProcessHandle.current().pid(),
                    "instance", started.instance().id(), "task", started.task().id(), "activity", started.activity().id())));
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(8);
            while (!Files.exists(done) && System.nanoTime() < deadline) Thread.sleep(250);
            assertThat(Files.exists(done)).as("Live F018 browser check signal").isTrue();
            var result = reads.findTaskSubmission(started.task().id()).orElseThrow().result();
            assertThat(result.toJson()).isEqualTo("{\"approvedAmount\":1250.00,\"note\":\"Live sandbox check\"}");
            assertThat(jdbc.queryForList("SELECT quantity FROM wf_test.service_effect WHERE instance_id=?", Long.class,
                    started.instance().id())).containsExactly(125000L);
        } finally {
            Files.deleteIfExists(done);
            Files.deleteIfExists(state);
        }
    }

    @AfterEach void cleanup() {
        failCompletion.set(false);
        tx().executeWithoutResult(t->{
            for(long id:instances) {
                jdbc.update("DELETE FROM wf_test.service_effect WHERE instance_id=?",id);
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
            if(familyId!=null)jdbc.update("DELETE FROM wf.workflow_definition WHERE id=? AND workflow_key=?",familyId,ownedDefinitionKey);
            if(ownedTaskType!=null)jdbc.update("DELETE FROM wf.workflow_task_type WHERE id=? AND code='SANDBOX_REVIEW'",ownedTaskType);
        });
    }

}
