package io.github.leontecristian.workflow.testhost;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leontecristian.workflow.core.dto.definition.CheckWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionManagementContext;
import io.github.leontecristian.workflow.core.dto.definition.ImportWorkflowDraft;
import io.github.leontecristian.workflow.core.dto.definition.TaskTypeCapability;
import io.github.leontecristian.workflow.core.dto.runtime.ActionField;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentContext;
import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.ResolvedTask;
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
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
import io.github.leontecristian.workflow.core.dto.tasktype.CreateTaskType;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.runtime.SingleTaskAssignmentResolver;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowExecutionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowServiceAction;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import io.github.leontecristian.workflow.testhost.configuration.integration.CompletionAdapterIntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Viewer routes and the sandbox adapter, on the guarded test catalog only.
 * Command calls below only prepare representative retained viewer state.
 */
@SpringBootTest(classes = WorkflowTestHostApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"workflow.console.enabled=true", "workflow.console.capabilities=none",
                "spring.main.allow-bean-definition-overriding=true",
                "workflow.console.admin.username=f013-admin", "workflow.console.admin.password=f013-local-test-password",
                "workflow.console.reviewer.username=f013-reviewer", "workflow.console.reviewer.password=f013-local-test-password"})
@Import(SharedInstanceViewerIT.Adapters.class)
class SharedInstanceViewerIT extends CompletionAdapterIntegrationTest {
    @Autowired WorkflowDefinitionService definitions;
    @Autowired TaskTypeCatalogService catalog;
    @Autowired WorkflowExecutionService engine;
    @Autowired WorkflowRuntimeQueryService reads;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired WebApplicationContext web;
    @LocalServerPort int port;
    private final String marker = "f013." + UUID.randomUUID();
    private static final String TASK_CODE = "F013_" + UUID.randomUUID().toString().replace("-", "");
    private final List<Long> instances = new ArrayList<>(), versions = new ArrayList<>(), families = new ArrayList<>();
    private Long taskType;
    private final ObjectMapper json = new ObjectMapper();
    private TransactionTemplate tx() { return new TransactionTemplate(transactions); }
    private WorkflowCommandContext actor(String actor) { return new WorkflowCommandContext(ActorKind.USER, actor, Instant.now(), marker); }
    private Path repository() { return Files.isDirectory(Path.of("docs")) ? Path.of("") : Path.of(".."); }
    private long publish(String name) throws Exception {
        var document = (ObjectNode) json.readTree(Files.readString(repository().resolve("docs/features/F011-sequential-human-stages/" + name + ".json")));
        document.put("key", marker + "." + name);
        for (var node : document.path("nodes")) {
            if (node.path("type").asText().equals("HUMAN_TASK")) {
                ((ObjectNode) node).put("taskType", TASK_CODE);
                ((ObjectNode) node.path("assignment")).put("resolver", "f013.reviewer");
            }
            if (node.path("type").asText().equals("SERVICE_TASK")) ((ObjectNode) node).put("action", "f013.quantity");
        }
        return tx().execute(status -> {
            var context = new DefinitionManagementContext(marker, Instant.now());
            var draft = definitions.importDraft(new ImportWorkflowDraft(document.toString()), context).after();
            versions.add(draft.id()); families.add(draft.definitionId());
            var result = definitions.publish(new CheckWorkflowDefinition(draft.id(), draft.lockVersion()), context);
            assertThat(result.published()).withFailMessage(() -> result.report().toString()).isTrue();
            return draft.id();
        });
    }
    private long start(long version, StartInputs inputs) {
        return tx().execute(status -> {
            var result = engine.start(new StartWorkflow(UUID.randomUUID(), version, null, null, null), inputs, actor(marker), null).execution();
            instances.add(result.instance().id()); return result.instance().id();
        });
    }
    private void advance(long instance) {
        var task = reads.listTasks(instance, 0, 100).items().stream().filter(t -> t.status() == TaskStatus.OPEN).findFirst().orElseThrow();
        tx().executeWithoutResult(status -> engine.completeTask(new CompleteWorkflowTask(UUID.randomUUID(), instance, task.id(),
                reads.findInstance(instance).orElseThrow().lockVersion(), "APPROVE", "Viewer fixture {\"n\":9007199254740993,\"d\":12500.00}"), actor(task.assignment().ref())));
    }
    private MockHttpSession session(String actor, String role) {
        var session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(actor, "unused", List.of(new SimpleGrantedAuthority(role)))));
        return session;
    }
    private String read(MockMvc mvc, MockHttpSession session, String path, int status) throws Exception {
        var request = get("/console/api" + path); if (session != null) request.session(session);
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).withFailMessage("%s", response.getContentAsString()).isEqualTo(status);
        assertThat(response.getContentType()).startsWith("application/json"); return response.getContentAsString();
    }
    @Test void extractedReadsAndRetainedHostAccess() throws Exception {
        taskType = tx().execute(status -> catalog.create(new CreateTaskType(TASK_CODE, marker, null, true)).after().id());
        long sequential = start(publish("sequential-review"), StartInputs.empty()); advance(sequential);
        long optional = start(publish("optional-stage-review"), new StartInputs(Map.of("quantity", new TextLiteral("5")))); advance(optional);
        var mvc = MockMvcBuilders.webAppContextSetup(web).addFilters(web.getBean(FilterChainProxy.class)).build();
        MockHttpSession admin = session("f013-admin", "ROLE_CONSOLE_ADMIN"), current = session("f013-reviewer", "ROLE_CONSOLE_REVIEWER"), historical = session("f013-manager", "ROLE_CONSOLE_REVIEWER"), unrelated = session("unrelated", "ROLE_CONSOLE_REVIEWER");
        var before = reads.findInstance(sequential).orElseThrow();
        for (String suffix : List.of("", "/history", "/history/1", "/graph")) {
            read(mvc, null, "/instances/" + sequential + suffix, 401);
            read(mvc, unrelated, "/instances/" + sequential + suffix, 404);
            int reviewerStatus = suffix.startsWith("/history") ? 404 : 200;
            read(mvc, current, "/instances/" + sequential + suffix, reviewerStatus);
            read(mvc, historical, "/instances/" + sequential + suffix, reviewerStatus);
        }
        var graph = json.readTree(read(mvc, admin, "/instances/" + sequential + "/graph", 200));
        assertThat(graph.path("instance").path("currentNodeKey").asText()).isEqualTo("PARTNER");
        var history = json.readTree(read(mvc, admin, "/instances/" + sequential + "/history?limit=1", 200));
        assertThat(history.path("items").size()).isEqualTo(1);
        read(mvc, admin, "/instances/" + sequential + "/history?afterSequence=" + history.path("nextAfterSequence").asText(), 200);
        var event = json.readTree(read(mvc, admin, "/instances/" + sequential + "/history/5", 200));
        assertThat(event.path("detailsJson").asText()).contains("9007199254740993", "12500.00");
        assertThat(event.path("summary").path("actorRef").asText()).isEqualTo("f013-manager");
        assertThat(read(mvc, historical, "/instances/" + sequential + "/history/5", 404))
                .doesNotContain("Viewer fixture", "detailsJson", "f013-manager");
        String business = "/instances/" + sequential + "/business-history";
        for (String suffix : List.of("", "/1")) {
            read(mvc, null, business + suffix, 401);
            assertThat(read(mvc, unrelated, business + suffix, 404))
                    .doesNotContain("Workflow started", "eventRef", "f013-manager");
        }
        String currentBody = read(mvc, current, business, 200);
        // Instance visibility comes from assignment. The open partner task has no linked sandbox request.
        assertThat(currentBody).contains("Workflow started", "Review opened")
                .doesNotContain("Task opened", "Decision recorded", "historySequence", "commandId", "actorRef", "f013-manager");
        assertThat(read(mvc, current, business + "/5", 404)).doesNotContain("Viewer fixture", "outcomeLabel", "detailsJson");
        var businessPage = json.readTree(read(mvc, historical, business + "?limit=1", 200));
        String cursor = businessPage.path("nextCursor").asText();
        assertThat(cursor).matches("[A-Za-z0-9_-]{1,200}").doesNotMatch("[0-9]+");
        assertThat(businessPage.has("nextAfterSequence")).isFalse();
        var nextPage = json.readTree(read(mvc, historical, business + "?cursor=" + cursor, 200));
        var decision = java.util.stream.StreamSupport.stream(nextPage.path("items").spliterator(), false)
                .filter(item -> "Decision recorded".equals(item.path("label").asText())).findFirst().orElseThrow();
        String eventRef = decision.path("eventRef").asText();
        assertThat(decision.path("actorLabel").asText()).isEqualTo("You");
        assertThat(eventRef).matches("[A-Za-z0-9_-]{1,200}").doesNotMatch("[0-9]+");
        String eventBody = read(mvc, historical, business + "/" + eventRef, 200);
        assertThat(eventBody).doesNotContain("detailsJson", "actorRef", "commandId", "correlationId",
                "detailsSchemaVersion", "outcomeKey", "historySequence", "f013-manager");
        var businessEvent = json.readTree(eventBody);
        assertThat(businessEvent.path("summary").path("label").asText()).isEqualTo("Decision recorded");
        assertThat(businessEvent.path("outcomeLabel").asText()).isEqualTo("Approve");
        assertThat(businessEvent.path("comment").asText()).contains("Viewer fixture", "9007199254740993");
        assertThat(businessEvent.path("taskId").asText()).isEqualTo(event.path("summary").path("taskId").asText());
        assertThat(json.readTree(read(mvc, historical, business + "/5", 200)).path("comment"))
                .isEqualTo(businessEvent.path("comment"));
        read(mvc, current, business + "/" + eventRef, 404);
        read(mvc, current, business + "?cursor=" + cursor, 400);
        read(mvc, historical, business + "/" + cursor, 404);
        read(mvc, historical, business + "?cursor=" + eventRef, 400);
        read(mvc, historical, "/instances/" + optional + "/business-history/" + eventRef, 404);
        read(mvc, historical, "/instances/" + optional + "/business-history?cursor=" + cursor, 400);
        assertThat(read(mvc, admin, business + "/5", 200)).doesNotContain("detailsJson", "actorRef", "commandId");
        var skipped = json.readTree(read(mvc, admin, "/instances/" + optional + "/graph", 200));
        assertThat(skipped.path("instance").path("status").asText()).isEqualTo("COMPLETED");
        String optionalBusiness = "/instances/" + optional + "/business-history";
        var automatic = java.util.stream.StreamSupport.stream(
                        json.readTree(read(mvc, historical, optionalBusiness, 200)).path("items").spliterator(), false)
                .filter(item -> "Automatic step completed".equals(item.path("label").asText())).findFirst().orElseThrow();
        String automaticBody = read(mvc, historical, optionalBusiness + "/" + automatic.path("eventRef").asText(), 200);
        assertThat(json.readTree(automaticBody).path("stepName").asText()).isEqualTo("Record quantity");
        assertThat(automaticBody).doesNotContain("f013.quantity", "\"nodeKey\"", "\"inputs\"", "\"outputs\"", "\"detailsJson\"");
        assertThat(reads.findInstance(sequential).orElseThrow()).isEqualTo(before);
        if (Boolean.getBoolean("f013.browserHold")) {
            Path local = repository().resolve(".local"); Files.createDirectories(local);
            Files.writeString(local.resolve("f013-runtime.json"), json.writeValueAsString(Map.of("port", port, "pid", ProcessHandle.current().pid(), "active", sequential, "skipped", optional)));
            Path done = local.resolve("f013-browser.done"); long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(12);
            while (!Files.exists(done) && System.nanoTime() < deadline) Thread.sleep(250);
            assertThat(Files.exists(done)).as("Focused browser completion signal").isTrue(); Files.deleteIfExists(done);
        }
    }
    @AfterEach void cleanup() {
        tx().executeWithoutResult(status -> {
            for (long instance : instances) {
                jdbc.update("DELETE FROM wf_test.workflow_event_outbox WHERE instance_id=?", instance);
                jdbc.update("DELETE FROM wf.workflow_command_receipt WHERE instance_id=?", instance);
                jdbc.update("DELETE FROM wf.workflow_history WHERE instance_id=?", instance);
                jdbc.update("DELETE FROM wf.workflow_variable WHERE instance_id=?", instance);
                jdbc.update("DELETE FROM wf.workflow_task WHERE activity_id IN (SELECT id FROM wf.workflow_activity WHERE instance_id=?)", instance);
                jdbc.update("DELETE FROM wf.workflow_activity WHERE instance_id=?", instance);
                jdbc.update("DELETE FROM wf.workflow_instance WHERE id=?", instance);
            }
            for (long version : versions) jdbc.update("DELETE FROM wf.workflow_definition_version WHERE id=?", version);
            for (long family : families) jdbc.update("DELETE FROM wf.workflow_definition WHERE id=? AND workflow_key LIKE ?", family, marker + ".%");
            if (taskType != null) jdbc.update("DELETE FROM wf.workflow_task_type WHERE id=? AND code=?", taskType, TASK_CODE);
        });
        for (long instance : instances) assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_instance WHERE id=?", Integer.class, instance)).isZero();
    }
    @TestConfiguration
    static class Adapters {
        @Bean
        ViewerFixtureHost viewerFixtureHost() {
            return new ViewerFixtureHost();
        }

        @Bean
        TaskTypeCapability viewerTaskCapability() {
            return new TaskTypeCapability(TASK_CODE, Set.of("APPROVE", "REJECT"));
        }
    }
    static class ViewerFixtureHost implements SingleTaskAssignmentResolver, WorkflowServiceAction {
        @Override public Set<io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind> targetKinds() { return Set.of(io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind.USER); }
        public String identifier() { return "f013.reviewer"; }
        public SingleAssignmentResult resolve(AssignmentContext context) { return new SingleAssignmentResult(List.of(new ResolvedTask("review", new io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget(io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind.USER, context.humanNodeKey().equals("MANAGER") ? "f013-manager" : "f013-reviewer"))), null); }
        public ServiceActionDescriptor descriptor() { return new ServiceActionDescriptor("f013.quantity", List.of(new ActionField("quantity", VariableValueType.LONG, true, false)), List.of(new ActionField("recordedQuantity", VariableValueType.LONG, true, false))); }
        public ServiceActionResult execute(ServiceActionContext context, Map<String, WorkflowValue> inputs) { return new ServiceActionResult(Map.of("recordedQuantity", inputs.get("quantity"))); }
    }
}
