package io.github.leontecristian.workflow.testhost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
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
import io.github.leontecristian.workflow.testhost.configuration.integration.CompletionAdapterIntegrationTest;
import io.github.leontecristian.workflow.testhost.dto.runtime.SandboxStart;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;
import io.github.leontecristian.workflow.testhost.configuration.integration.RuntimeCommandFixture;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeTransaction;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleRequestIdentity;
import io.github.leontecristian.workflow.web.integration.WorkflowRequestIdentity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.concurrent.TimeUnit;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

/** One guarded DB setup exercises the moved mapping, host transaction and optional browser fixture. */
@SpringBootTest(classes=WorkflowTestHostApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"workflow.console.enabled=true","workflow.console.capabilities=sample",
        "workflow.console.admin.username=f015-admin","workflow.console.admin.password=f015-local-test-password",
        "workflow.console.fail-service-node=failRecordAmount",
        "workflow.console.reviewer.username=f015-reviewer","workflow.console.reviewer.password=f015-local-test-password"})
@Import({SharedRuntimeIT.FailureFixture.class, RuntimeCommandFixture.class})
class SharedRuntimeIT extends CompletionAdapterIntegrationTest {
    private static final String PRECONDITION = "X-Task-Precondition";
    @Autowired WorkflowDefinitionService definitions;
    @Autowired WorkflowRuntimeQueryService reads;
    @Autowired TaskTypeCatalogService catalog;
    @MockitoSpyBean SandboxRequestStore requests;
    @Autowired ConsoleRuntimeTransaction runtime;
    @Autowired RuntimeCommandFixture runtimeCommands;
    @MockitoSpyBean WorkflowExecutionService execution;
    @MockitoSpyBean ConsoleRequestIdentity identity;
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

    @TestConfiguration(proxyBeanMethods=false)
    static class FailureFixture {
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
    private String completion(UUID command) throws Exception {
        return json.writeValueAsString(Map.of("commandId",command.toString(),"outcomeKey","APPROVE"));
    }
    private String replayBody(WorkflowExecutionResult started,UUID command) throws Exception {
        return json.writeValueAsString(Map.of("commandId",command.toString(),"outcomeKey","APPROVE",
            "taskId",Long.toString(started.task().id())));
    }

    private JsonNode taskContract(MockMvc mvc, MockHttpSession principal, long taskId) throws Exception {
        var response = mvc.perform(get("/console/api/tasks/" + taskId + "/contract").session(principal))
                .andReturn().getResponse();
        assertThat(response.getStatus()).withFailMessage(response.getContentAsString()).isEqualTo(200);
        return json.readTree(response.getContentAsString());
    }

    private String reviewedVersion(MockMvc mvc, MockHttpSession principal, long taskId) throws Exception {
        String precondition = taskContract(mvc, principal, taskId).path("precondition").textValue();
        assertThat(precondition).isNotBlank();
        return precondition;
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
        String endpoint="/console/api/tasks/"+task+"/complete";
        String precondition = reviewedVersion(mvc, reviewer, task);
        UUID command=UUID.randomUUID();
        assertThat(mvc.perform(post(endpoint).session(reviewer).contentType("application/json").header(PRECONDITION, precondition)
            .content(completion(command))).andReturn().getResponse().getStatus()).isEqualTo(403);
        var core = AopTestUtils.<WorkflowExecutionService>getUltimateTargetObject(execution);
        var completions = new AtomicInteger();
        var transaction = new AtomicReference<Map<Object, Object>>();
        var rollbackStatus = new AtomicInteger(TransactionSynchronization.STATUS_UNKNOWN);
        doAnswer(invocation -> {
            var input = invocation.<CompleteWorkflowTask>getArgument(0);
            var actor = invocation.<WorkflowCommandContext>getArgument(1);
            assertThat(actor.actorRef()).isEqualTo("f015-reviewer");
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            var result = (WorkflowCommandResult) invocation.callRealMethod();
            if (completions.incrementAndGet() == 1) {
                // Read the completed submission on the same transaction used by the HTTP command.
                assertThat(reads.findTaskSubmission(input.taskId()).orElseThrow().commandId()).isEqualTo(command);
                return result;
            }
            assertThat(result.replayed()).isTrue();
            transaction.set(Map.copyOf(TransactionSynchronizationManager.getResourceMap()));
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    rollbackStatus.set(status);
                }
            });
            throw new WorkflowExecutionException(Category.CONCURRENT_COMMAND);
        }).when(core).completeTask(argThat(input -> input != null && input.commandId().equals(command)), any());
        assertThat(web.getBeansOfType(WorkflowRequestIdentity.class).values()).containsExactly(identity);
        clearInvocations(identity);
        var accepted=mvc.perform(post(endpoint).session(reviewer).contentType("application/json")
            .header(csrf.path("csrfHeaderName").asText(),csrf.path("csrfToken").asText())
            .header(PRECONDITION, precondition).content(completion(command))).andReturn().getResponse();
        assertThat(accepted.getStatus()).withFailMessage(accepted.getContentAsString()).isEqualTo(200);
        assertThat(json.readTree(accepted.getContentAsString()).path("replayed").asBoolean()).isFalse();
        var recoveries = new AtomicInteger();
        doAnswer(invocation -> {
            if (recoveries.getAndIncrement() == 0) {
                assertThat(rollbackStatus.get()).isEqualTo(TransactionSynchronization.STATUS_ROLLED_BACK);
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
                assertThat(TransactionSynchronizationManager.getResourceMap()).isNotEmpty()
                        .isNotEqualTo(transaction.get());
                assertThat(invocation.<WorkflowCommandContext>getArgument(1).actorRef()).isEqualTo("f015-reviewer");
            }
            return invocation.callRealMethod();
        }).when(core).findReplay(argThat((CompleteWorkflowTask input) -> input != null
                && input.commandId().equals(command)), any());
        var recovered = mvc.perform(post(endpoint).session(reviewer).contentType("application/json")
            .header(csrf.path("csrfHeaderName").asText(),csrf.path("csrfToken").asText())
            .header(PRECONDITION, precondition)
            .content(completion(command))).andReturn().getResponse();
        assertThat(recovered.getStatus()).withFailMessage(recovered.getContentAsString()).isEqualTo(200);
        assertThat(json.readTree(recovered.getContentAsString()).path("replayed").asBoolean()).isTrue();
        assertThat(completions.get()).isEqualTo(2);
        assertThat(recoveries.get()).isEqualTo(1);
        assertThat(mvc.perform(get("/console/api/instances/" + instance + "/history").session(reviewer))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mvc.perform(get("/console/api/instances").session(admin))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(get("/console/api/definitions").session(reviewer)).andReturn().getResponse().getStatus())
                .isEqualTo(403);
        verify(identity, times(5)).requireActorRef();

        var replay=mvc.perform(post("/console/api/commands/complete/replay").session(reviewer).contentType("application/json")
            .header(csrf.path("csrfHeaderName").asText(),csrf.path("csrfToken").asText()).header(PRECONDITION, precondition)
            .content(replayBody(started,command))).andReturn().getResponse();
        assertThat(replay.getStatus()).withFailMessage(replay.getContentAsString()).isEqualTo(200);
        assertThat(json.readTree(replay.getContentAsString()).path("found").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_history WHERE instance_id=? AND event_type='TASK_COMPLETED'",Integer.class,instance)).isEqualTo(1);
        var pending=start();long before=pending.instance().lockVersion();
        String pendingPrecondition = reviewedVersion(mvc, reviewer, pending.task().id());
        var missing=mvc.perform(post("/console/api/commands/complete/replay").session(reviewer).contentType("application/json")
            .header(csrf.path("csrfHeaderName").asText(),csrf.path("csrfToken").asText()).header(PRECONDITION, pendingPrecondition)
            .content(replayBody(pending,UUID.randomUUID()))).andReturn().getResponse();
        assertThat(missing.getStatus()).isEqualTo(200);assertThat(json.readTree(missing.getContentAsString()).path("found").asBoolean()).isFalse();
        assertThat(reads.findInstance(pending.instance().id()).orElseThrow().lockVersion()).isEqualTo(before);
        UUID failedCommand = UUID.randomUUID();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("Configured post-result failure");
        }).when(core).completeTask(argThat(input -> input != null && input.commandId().equals(failedCommand)), any());
        String pendingEndpoint = "/console/api/tasks/" + pending.task().id() + "/complete";
        var failed = mvc.perform(post(pendingEndpoint).session(reviewer).contentType("application/json")
                .header(csrf.path("csrfHeaderName").asText(), csrf.path("csrfToken").asText())
                .header(PRECONDITION, pendingPrecondition).content(completion(failedCommand))).andReturn().getResponse();
        assertThat(failed.getStatus()).isEqualTo(500);
        assertThat(failed.getContentAsString()).doesNotContain("Configured");

        var request = requests.findByInstance(pending.instance().id()).orElseThrow();
        var store = AopTestUtils.<SandboxRequestStore>getUltimateTargetObject(requests);
        doAnswer(invocation -> {
            // Make the production conditional write lose after the action read its basis.
            jdbc.update("UPDATE wf_test.sandbox_request SET lock_version=lock_version+1 WHERE id=?", request.id());
            return invocation.callRealMethod();
        }).when(store).updateStatus(eq(request.id()), eq(pending.instance().id()), eq(request.lockVersion()),
                eq("IN_REVIEW"), eq("APPROVED"));
        UUID conflictCommand = UUID.randomUUID();
        var conflict = mvc.perform(post(pendingEndpoint).session(reviewer).contentType("application/json")
                .header(csrf.path("csrfHeaderName").asText(), csrf.path("csrfToken").asText())
                .header(PRECONDITION, pendingPrecondition).content(completion(conflictCommand))).andReturn();
        assertThat(conflict.getResolvedException()).isInstanceOf(WorkflowExecutionException.class)
                .hasRootCauseInstanceOf(io.github.leontecristian.workflow.testhost.exception.console.SandboxRequestConflict.class);
        assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
        assertThat(json.readTree(conflict.getResponse().getContentAsString())).isEqualTo(json.valueToTree(Map.of(
                "code", "SANDBOX_REQUEST_CONFLICT", "message", "SANDBOX_REQUEST_CONFLICT", "diagnostics", List.of(),
                "correlationId", conflict.getRequest().getAttribute("console.correlation"))));
        verify(core, never()).findReplay(argThat((CompleteWorkflowTask input) -> input != null
                && input.commandId().equals(conflictCommand)), any());
        assertThat(requests.find(request.id())).isEqualTo(request);
        var conflictReplay = mvc.perform(post("/console/api/commands/complete/replay").session(reviewer)
                .contentType("application/json").header(csrf.path("csrfHeaderName").asText(), csrf.path("csrfToken").asText())
                .header(PRECONDITION, pendingPrecondition).content(replayBody(pending, conflictCommand))).andReturn().getResponse();
        assertThat(conflictReplay.getStatus()).isEqualTo(200);
        assertThat(json.readTree(conflictReplay.getContentAsString()).path("found").asBoolean()).isFalse();

        // Advance the test-owned instance version without changing the open task or its business request.
        assertThat(jdbc.update("UPDATE wf.workflow_instance SET lock_version=lock_version+1 WHERE id=? AND lock_version=?",
                pending.instance().id(), before)).isEqualTo(1);
        var stale = mvc.perform(post(pendingEndpoint).session(reviewer).contentType("application/json")
                .header(csrf.path("csrfHeaderName").asText(), csrf.path("csrfToken").asText())
                .header(PRECONDITION, pendingPrecondition).content(completion(UUID.randomUUID())))
                .andReturn().getResponse();
        assertThat(stale.getStatus()).isEqualTo(409);
        assertThat(json.readTree(stale.getContentAsString()).path("code").asText()).isEqualTo("STALE_VERSION");
        assertThat(reads.findInstance(pending.instance().id()).orElseThrow().lockVersion()).isEqualTo(before + 1);
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

    @Test void amountReviewReadProjectionsExposeCreatedOnlyInThePersonalInbox() throws Exception {
        prepareStructuredDefinition(false);
        var started = startStructured();
        long taskId = started.task().id();
        String taskUrl = "/console/api/tasks/" + taskId;
        String instanceId = Long.toString(started.instance().id());
        var mvc = MockMvcBuilders.webAppContextSetup(web).addFilters(web.getBean(FilterChainProxy.class)).build();
        var reviewer = session("f015-reviewer", "ROLE_CONSOLE_REVIEWER");

        for (String url : List.of(taskUrl, "/console/api/instances/" + instanceId + "/tasks", "/console/api/tasks/mine")) {
            boolean inbox = url.endsWith("/mine");
            var request = get(url).session(reviewer);
            if (inbox) request.param("instanceId", instanceId);
            var response = mvc.perform(request).andReturn().getResponse();
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsString()).contains("Review approved amount").doesNotContain("taskKey", "instanceId",
                    "activityId", "assigneeRef", "taskTypeId", "f015-reviewer");
            if (inbox) {
                var row = json.readTree(response.getContentAsString()).path("items").get(0);
                assertThat(row.path("id").textValue()).isEqualTo(Long.toString(taskId));
                assertThat(Instant.parse(row.path("createdAt").textValue())).isEqualTo(reads.findTask(taskId).orElseThrow().createdAt());
            } else {
                assertThat(response.getContentAsString()).doesNotContain("createdAt");
            }
        }
    }

    @Test void amountReviewHttpProjectsAuthorizedFieldsAndReplaysFrozenExactResult() throws Exception {
        prepareStructuredDefinition(false);
        var started = startStructured();
        long taskId = started.task().id();
        String taskUrl = "/console/api/tasks/" + taskId;
        var mvc = MockMvcBuilders.webAppContextSetup(web).addFilters(web.getBean(FilterChainProxy.class)).build();
        var reviewer = session("f015-reviewer", "ROLE_CONSOLE_REVIEWER");
        var deniedActor = session("f015-reviewer2", "ROLE_CONSOLE_REVIEWER");
        var admin = session("f015-admin", "ROLE_CONSOLE_ADMIN");

        var reviewerContract = taskContract(mvc, reviewer, taskId);
        assertThat(reviewerContract.path("task").path("ownershipLabel").textValue()).isEqualTo("Assigned to you");
        assertThat(reviewerContract.path("canComplete").asBoolean()).isTrue();
        assertThat(reviewerContract.path("resultContract").path("form").path("fields").get(0).path("label").textValue())
                .isEqualTo("Approved amount");
        assertThat(reviewerContract.toString()).doesNotContain("\"schema\"", "instanceLockVersion", "instanceId",
                "activityId", "assigneeRef", "claimPolicy", "actorRef", "diagnostics", "f015-reviewer");
        var adminContract = taskContract(mvc, admin, taskId);
        assertThat(adminContract.has("diagnostics")).isFalse();
        assertThat(adminContract.path("canComplete").asBoolean()).isFalse();
        assertThat(adminContract.has("taskType")).isFalse();
        assertThat(adminContract.path("outcomes").get(0).has("key")).isFalse();
        var details = mvc.perform(get(taskUrl + "/diagnostics").session(admin)).andReturn().getResponse();
        assertThat(details.getStatus()).isEqualTo(200);
        var diagnostics = json.readTree(details.getContentAsString());
        assertThat(diagnostics.path("task").path("id").textValue()).isEqualTo(Long.toString(taskId));
        assertThat(diagnostics.path("contract").path("resultContract").path("schema").isObject()).isTrue();
        for (var ordinarySession : List.of(reviewer, deniedActor)) {
            var hidden = mvc.perform(get(taskUrl + "/diagnostics").session(ordinarySession)).andReturn().getResponse();
            assertThat(hidden.getStatus()).isEqualTo(404);
            assertThat(hidden.getContentAsString()).doesNotContain("schema", "instanceLockVersion", "f015-reviewer");
        }
        var requestedDiagnostics = mvc.perform(get(taskUrl + "/contract?diagnostics=true").session(reviewer))
                .andReturn().getResponse();
        assertThat(requestedDiagnostics.getContentAsString()).doesNotContain("\"diagnostics\"", "\"schema\"");
        var denied = mvc.perform(get(taskUrl + "/contract").session(deniedActor)).andReturn().getResponse();
        assertThat(denied.getStatus()).isEqualTo(404);
        assertThat(denied.getContentAsString()).doesNotContain("Approved amount", "precondition", "resultContract", "instanceLockVersion");

        String instanceId = Long.toString(started.instance().id());
        var adminList = get("/console/api/tasks/administration").param("instanceId", instanceId);
        var taskAdministration = mvc.perform(adminList.session(admin)).andReturn().getResponse();
        assertThat(taskAdministration.getStatus()).withFailMessage(taskAdministration.getContentAsString()).isEqualTo(200);
        var adminRow = json.readTree(taskAdministration.getContentAsString()).path("items").get(0);
        assertThat(adminRow.path("id").textValue()).isEqualTo(Long.toString(taskId));
        assertThat(adminRow.path("instanceId").textValue()).isEqualTo(instanceId);
        assertThat(Instant.parse(adminRow.path("createdAt").textValue())).isEqualTo(reads.findTask(taskId).orElseThrow().createdAt());
        assertThat(mvc.perform(adminList.session(reviewer)).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(get("/console/api/instances/" + instanceId).session(admin))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(get("/console/api/instances/" + instanceId).session(deniedActor))
                .andReturn().getResponse().getStatus()).isEqualTo(404);

        var csrf = json.readTree(mvc.perform(get("/console/api/session").session(reviewer))
                .andReturn().getResponse().getContentAsString());
        String csrfHeader = csrf.path("csrfHeaderName").textValue();
        String csrfToken = csrf.path("csrfToken").textValue();
        String precondition = reviewerContract.path("precondition").textValue();
        assertThat(precondition).isNotBlank();
        String attempt = json.createObjectNode().put("commandId", UUID.randomUUID().toString())
                .put("outcomeKey", "COMPLETE").toString();
        String invalid = attempt.substring(0, attempt.length() - 1) + ",\"result\":{\"approvedAmount\":-1,\"note\":\"\"}}";
        var rejected = mvc.perform(post(taskUrl + "/complete").session(reviewer).contentType("application/json")
                .header(csrfHeader, csrfToken).header(PRECONDITION, precondition).content(invalid))
                .andReturn().getResponse();
        assertThat(rejected.getStatus()).withFailMessage(rejected.getContentAsString()).isEqualTo(422);
        assertThat(rejected.getContentAsString()).contains("/approvedAmount").doesNotContain("schemaPath", "instanceId", "execution");

        String commandId = UUID.randomUUID().toString();
        String frozen = "{\"commandId\":\"" + commandId
                + "\",\"outcomeKey\":\"COMPLETE\",\"comment\":\"Reviewed\",\"result\":{\"approvedAmount\":0.00,\"note\":\"\"}}";
        var completed = mvc.perform(post(taskUrl + "/complete").session(reviewer).contentType("application/json")
                .header(csrfHeader, csrfToken).header(PRECONDITION, precondition).content(frozen))
                .andReturn().getResponse();
        assertThat(completed.getStatus()).withFailMessage(completed.getContentAsString()).isEqualTo(200);
        assertThat(json.readTree(completed.getContentAsString()).path("taskStatus").textValue()).isEqualTo("COMPLETED");
        assertThat(completed.getContentAsString()).doesNotContain("execution", "instanceId", "historySequence", "receipt");

        String replayBody = "{\"taskId\":\"" + taskId + "\"," + frozen.substring(1);
        var replay = mvc.perform(post("/console/api/commands/complete/replay").session(reviewer).contentType("application/json")
                .header(csrfHeader, csrfToken).header(PRECONDITION, precondition).content(replayBody))
                .andReturn().getResponse();
        assertThat(replay.getStatus()).withFailMessage(replay.getContentAsString()).isEqualTo(200);
        assertThat(json.readTree(replay.getContentAsString()).path("result").path("replayed").asBoolean()).isTrue();
        assertThat(replay.getContentAsString()).doesNotContain("execution", "instanceId", "historySequence", "receipt", commandId);
        assertThat(taskContract(mvc, reviewer, taskId).has("precondition")).isFalse();

        var submission = mvc.perform(get(taskUrl + "/submission").session(reviewer)).andReturn().getResponse();
        assertThat(submission.getStatus()).isEqualTo(200);
        assertThat(submission.getContentAsString()).contains("\"approvedAmount\":0.00", "\"note\":\"\"", "Reviewed", "Complete", "You")
                .doesNotContain(commandId, "actorRef", "taskId", "outcomeKey", "historySequence", "instanceId", "diagnostics");
        var adminSubmission = mvc.perform(get(taskUrl + "/submission").session(admin)).andReturn().getResponse();
        assertThat(adminSubmission.getStatus()).isEqualTo(200);
        assertThat(adminSubmission.getContentAsString()).contains("\"actorLabel\":\"Sandbox reviewer\"", "\"approvedAmount\":0.00")
                .doesNotContain(commandId, "actorRef", "f015-reviewer", "diagnostics");
        var hiddenSubmission = mvc.perform(get(taskUrl + "/submission").session(deniedActor)).andReturn().getResponse();
        assertThat(hiddenSubmission.getStatus()).isEqualTo(404);
        assertThat(hiddenSubmission.getContentAsString()).doesNotContain("approvedAmount", commandId);
        var adminEvidence = mvc.perform(get(taskUrl + "/diagnostics").session(admin)).andReturn().getResponse();
        assertThat(adminEvidence.getStatus()).isEqualTo(200);
        assertThat(adminEvidence.getContentAsString()).contains("\"approvedAmount\":0.00", commandId, "f015-reviewer");
        var submissions = mvc.perform(get("/console/api/activities/" + started.activity().id() + "/submissions")
                .session(reviewer)).andReturn().getResponse();
        assertThat(submissions.getStatus()).isEqualTo(200);
        assertThat(submissions.getContentAsString()).contains("\"approvedAmount\":0.00")
                .doesNotContain(commandId, "actorRef", "outcomeKey", "historySequence", "instanceId", "diagnostics");
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
                assertThatThrownBy(() -> runtimeCommands.complete(rejected, reviewer)).isInstanceOfSatisfying(
                        WorkflowExecutionException.class, e -> assertThat(e.category()).isEqualTo(invalid.getValue()));
                assertThat(runtimeCommands.replayComplete(rejected, reviewer)).isEmpty();
                assertThat(reads.findTaskSubmission(first.taskId())).isEmpty();
                assertThat(reads.findInstance(instanceId).orElseThrow().lockVersion()).isEqualTo(first.expectedLockVersion());
            }
        }
        runtimeCommands.complete(first, reviewer);
        assertThat(reads.findTaskSubmission(first.taskId()).orElseThrow().result()).isEqualTo(accepted);
        assertThat(reads.listActivitySubmissions(started.activity().id()).items()).singleElement()
                .satisfies(submission -> assertThat(submission.result()).isEqualTo(accepted));

        if (failDownstream) {
            var current = reads.listTasks(instanceId).items().stream().filter(task -> task.id() != first.taskId()).findFirst().orElseThrow();
            long token = reads.findInstance(instanceId).orElseThrow().lockVersion();
            var failing = new CompleteWorkflowTask(UUID.randomUUID(), instanceId, current.id(), token, "COMPLETE", null, accepted);
            assertThatThrownBy(() -> runtimeCommands.complete(failing, reviewer)).isInstanceOfSatisfying(
                    WorkflowExecutionException.class, e -> {
                        assertThat(e.category()).isEqualTo(Category.SERVICE_ACTION_FAILED);
                        assertThat(e).hasRootCauseMessage("Configured service failure");
                    });
            assertThat(runtimeCommands.replayComplete(failing, reviewer)).isEmpty();
            assertThat(reads.findTaskSubmission(current.id())).isEmpty();
            assertThat(reads.findTaskSubmission(first.taskId()).orElseThrow().result()).isEqualTo(accepted);
            assertThat(reads.findInstance(instanceId).orElseThrow().lockVersion()).isEqualTo(token);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_test.service_effect WHERE instance_id=?", Integer.class, instanceId))
                    .isZero();
        } else {
            var reordered = new CompleteWorkflowTask(first.commandId(), instanceId, first.taskId(), first.expectedLockVersion(),
                    "COMPLETE", null, CompleteWorkflowTask.parseResult("{\"note\":\"Approved\",\"approvedAmount\":1250.00}"));
            assertThat(runtimeCommands.replayComplete(reordered, reviewer)).isPresent();
            var changed = new CompleteWorkflowTask(first.commandId(), instanceId, first.taskId(), first.expectedLockVersion(),
                    "COMPLETE", null, CompleteWorkflowTask.parseResult("{\"approvedAmount\":1250,\"note\":\"Approved\"}"));
            assertThatThrownBy(() -> runtimeCommands.replayComplete(changed, reviewer)).isInstanceOfSatisfying(
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
            assertThatThrownBy(() -> runtimeCommands.complete(command, reviewer)).isInstanceOfSatisfying(
                    WorkflowExecutionException.class, e -> {
                        assertThat(e.category()).isEqualTo(Category.SERVICE_ACTION_FAILED);
                        assertThat(e).hasRootCauseMessage("Configured service failure");
                    });
            assertThat(reads.listVariables(instanceId, null, 50, null).items()).isEmpty();
            assertThat(reads.findTaskSubmission(started.task().id())).isEmpty();
            assertThat(runtimeCommands.replayComplete(command, reviewer)).isEmpty();
            assertThat(reads.findInstance(instanceId).orElseThrow().lockVersion()).isEqualTo(command.expectedLockVersion());
            assertThat(effectQuantities(instanceId)).isEmpty();
            assertThat(reads.listHistory(instanceId).items()).noneMatch(item -> item.commandId().equals(command.commandId()));
            return;
        }

        runtimeCommands.complete(command, reviewer);
        assertThat(runtimeCommands.replayComplete(command, reviewer)).isPresent();
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
        runtimeCommands.complete(finance, reviewer);
        var managerTask = reads.listTasks(instanceId).items().stream()
                .filter(task -> task.id() != finance.taskId()).findFirst().orElseThrow();
        long token = reads.findInstance(instanceId).orElseThrow().lockVersion();
        var managerResult = CompleteWorkflowTask.parseResult("{\"approvedAmount\":800.000,\"note\":\"Manager\"}");
        var manager = new CompleteWorkflowTask(UUID.randomUUID(), instanceId, managerTask.id(), token,
                "COMPLETE", null, omitManagerResult ? null : managerResult);

        if (omitManagerResult) {
            assertThatThrownBy(() -> runtimeCommands.complete(manager, reviewer)).isInstanceOfSatisfying(
                    WorkflowExecutionException.class, e -> assertThat(e.category()).isEqualTo(Category.SUBMISSION_UNAVAILABLE));
            assertThat(runtimeCommands.replayComplete(manager, reviewer)).isEmpty();
            assertThat(reads.findTaskSubmission(manager.taskId())).isEmpty();
            assertThat(reads.findInstance(instanceId).orElseThrow().lockVersion()).isEqualTo(token);
            assertThat(reads.listHistory(instanceId).items()).noneMatch(item -> item.commandId().equals(manager.commandId()));
            assertThat(effectQuantities(instanceId)).isEmpty();
        } else {
            runtimeCommands.complete(manager, reviewer);
            assertThat(runtimeCommands.replayComplete(manager, reviewer)).isPresent();
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
        var replay = runtimeCommands.complete(entered.command(), reviewerContext("f015-reviewer"));
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
        var originalCsrf = json.readTree(mvc.perform(get("/console/api/session").session(originalReviewer))
                .andReturn().getResponse().getContentAsString());
        String originalClaimVersion = reviewedVersion(mvc, originalReviewer, taskId);
        String originalClaimId = UUID.randomUUID().toString();
        mvc.perform(post("/console/api/tasks/" + taskId + "/claim").session(originalReviewer)
                .contentType("application/json")
                .header(originalCsrf.path("csrfHeaderName").asText(), originalCsrf.path("csrfToken").asText())
                .header(PRECONDITION, originalClaimVersion)
                .content(json.createObjectNode().put("commandId", originalClaimId).toString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mvc.perform(post("/console/api/tasks/" + taskId + "/release").session(originalReviewer)
                .contentType("application/json")
                .header(originalCsrf.path("csrfHeaderName").asText(), originalCsrf.path("csrfToken").asText())
                .header(PRECONDITION, reviewedVersion(mvc, originalReviewer, taskId))
                .content(json.createObjectNode().put("commandId", UUID.randomUUID().toString()).toString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        var csrf = json.readTree(mvc.perform(get("/console/api/session").session(peer))
                .andReturn().getResponse().getContentAsString());
        var claimed = mvc.perform(post("/console/api/tasks/" + taskId + "/claim").session(peer)
                .contentType("application/json")
                .header(csrf.path("csrfHeaderName").asText(), csrf.path("csrfToken").asText())
                .header(PRECONDITION, reviewedVersion(mvc, peer, taskId))
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
        assertThat(historyResponse.getStatus()).isEqualTo(404);
        assertThat(historyResponse.getContentAsString()).doesNotContain("9007199254740993.00");
        var adminHistory = mvc.perform(get(event).session(session("f015-admin", "ROLE_CONSOLE_ADMIN")))
                .andReturn().getResponse();
        assertThat(adminHistory.getStatus()).isEqualTo(200);
        assertThat(json.readTree(adminHistory.getContentAsString()).path("detailsJson").asText()).contains("9007199254740993.00");

        var answer = CompleteWorkflowTask.parseResult("{\"approvedAmount\":1250.00,\"note\":\"Receiver answer\"}");
        var complete = new CompleteWorkflowTask(UUID.randomUUID(), instanceId, taskId, reads.findInstance(instanceId).orElseThrow().lockVersion(),
                "COMPLETE", null, answer);
        runtimeCommands.complete(complete, reviewerContext("f015-reviewer2"));
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
        var recoveredClaim = mvc.perform(post("/console/api/commands/claim/replay").session(originalReviewer)
                .contentType("application/json")
                .header(originalCsrf.path("csrfHeaderName").asText(), originalCsrf.path("csrfToken").asText())
                .header(PRECONDITION, originalClaimVersion)
                .content(json.createObjectNode().put("commandId", originalClaimId).put("taskId", Long.toString(taskId)).toString()))
                .andReturn().getResponse();
        assertThat(recoveredClaim.getStatus()).isEqualTo(200);
        var recovered = json.readTree(recoveredClaim.getContentAsString());
        assertThat(recovered.path("found").asBoolean()).isTrue();
        assertThat(recovered.path("result").path("taskStatus").asText()).isEqualTo("OPEN");
        assertThat(recovered.path("result").has("execution")).isFalse();
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
        assertThatThrownBy(() -> runtimeCommands.complete(command, reviewer)).isInstanceOfSatisfying(
                WorkflowExecutionException.class, error -> assertThat(error.category()).isEqualTo(expected));
        assertThat(reads.findInstance(instanceId).orElseThrow()).isEqualTo(started.instance());
        assertThat(reads.listTasks(instanceId).items()).containsExactly(started.task());
        assertThat(reads.findTaskSubmission(command.taskId())).isEmpty();
        assertThat(runtimeCommands.replayComplete(command, reviewer)).isEmpty();
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
        var progressed = runtimeCommands.complete(command, reviewerContext("f015-reviewer")).execution();
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
