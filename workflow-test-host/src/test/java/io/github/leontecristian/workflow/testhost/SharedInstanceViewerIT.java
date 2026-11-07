package io.github.leontecristian.workflow.testhost;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import io.github.leontecristian.workflow.core.service.definition.DefinitionCapabilityProvider;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.runtime.SingleTaskAssignmentResolver;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowExecutionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowServiceAction;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import io.github.leontecristian.workflow.testhost.configuration.integration.DatabaseIntegrationTest;
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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Four real read routes and the sandbox adapter, on the guarded test catalog only.
 * Command calls below only prepare representative retained viewer state.
 */
@SpringBootTest(classes = WorkflowTestHostApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"workflow.console.enabled=true", "workflow.console.capabilities=none",
                "spring.main.allow-bean-definition-overriding=true",
                "workflow.console.admin.username=f013-admin", "workflow.console.admin.password=f013-local-test-password",
                "workflow.console.reviewer.username=f013-reviewer", "workflow.console.reviewer.password=f013-local-test-password"})
class SharedInstanceViewerIT extends DatabaseIntegrationTest {
    @Autowired WorkflowDefinitionService definitions;
    @Autowired TaskTypeCatalogService catalog;
    @Autowired WorkflowExecutionService engine;
    @Autowired WorkflowRuntimeQueryService reads;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired WebApplicationContext web;
    @TestBean(name = "consoleCapabilities", methodName = "viewerFixtureHost")
    ViewerFixtureHost fixtures;
    @LocalServerPort int port;
    private final String marker = "f013." + UUID.randomUUID();
    private final String code = "F013_" + UUID.randomUUID().toString().replace("-", "");
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
                ((ObjectNode) node).put("taskType", code);
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
        fixtures.code = code;
        taskType = tx().execute(status -> catalog.create(new CreateTaskType(code, marker, null, true)).after().id());
        long sequential = start(publish("sequential-review"), StartInputs.empty()); advance(sequential);
        long optional = start(publish("optional-stage-review"), new StartInputs(Map.of("quantity", new TextLiteral("5")))); advance(optional);
        var mvc = MockMvcBuilders.webAppContextSetup(web).addFilters(web.getBean(FilterChainProxy.class)).build();
        MockHttpSession admin = session("f013-admin", "ROLE_CONSOLE_ADMIN"), current = session("f013-reviewer", "ROLE_CONSOLE_REVIEWER"), historical = session("f013-manager", "ROLE_CONSOLE_REVIEWER"), unrelated = session("unrelated", "ROLE_CONSOLE_REVIEWER");
        var before = reads.findInstance(sequential).orElseThrow();
        for (String suffix : List.of("", "/history", "/history/1", "/graph")) {
            read(mvc, null, "/instances/" + sequential + suffix, 401);
            read(mvc, unrelated, "/instances/" + sequential + suffix, 404);
            read(mvc, current, "/instances/" + sequential + suffix, 200);
            read(mvc, historical, "/instances/" + sequential + suffix, 200);
        }
        var graph = json.readTree(read(mvc, admin, "/instances/" + sequential + "/graph", 200));
        assertThat(graph.path("instance").path("currentNodeKey").asText()).isEqualTo("PARTNER");
        var history = json.readTree(read(mvc, admin, "/instances/" + sequential + "/history?limit=1", 200));
        assertThat(history.path("items").size()).isEqualTo(1);
        read(mvc, admin, "/instances/" + sequential + "/history?afterSequence=" + history.path("nextAfterSequence").asText(), 200);
        var event = json.readTree(read(mvc, admin, "/instances/" + sequential + "/history/5", 200));
        assertThat(event.path("detailsJson").asText()).contains("9007199254740993", "12500.00");
        var skipped = json.readTree(read(mvc, admin, "/instances/" + optional + "/graph", 200));
        assertThat(skipped.path("instance").path("status").asText()).isEqualTo("COMPLETED");
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
            if (taskType != null) jdbc.update("DELETE FROM wf.workflow_task_type WHERE id=? AND code=?", taskType, code);
        });
        for (long instance : instances) assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_instance WHERE id=?", Integer.class, instance)).isZero();
    }
    static ViewerFixtureHost viewerFixtureHost() { return new ViewerFixtureHost(); }
    static class ViewerFixtureHost implements DefinitionCapabilityProvider, SingleTaskAssignmentResolver, WorkflowServiceAction {
        @Override public Set<io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind> targetKinds() { return Set.of(io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind.USER); }
        String code;
        public String identifier() { return "f013.reviewer"; }
        public DefinitionCapabilities snapshot() { return new DefinitionCapabilities(Set.of(new SingleAssignmentCapability(identifier(), Set.of(io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind.USER))), code == null ? Set.of() : Set.of(new TaskTypeCapability(code, Set.of("APPROVE", "REJECT")))); }
        public SingleAssignmentResult resolve(AssignmentContext context) { return new SingleAssignmentResult(List.of(new ResolvedTask("review", new io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget(io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind.USER, context.humanNodeKey().equals("MANAGER") ? "f013-manager" : "f013-reviewer"))), null); }
        public ServiceActionDescriptor descriptor() { return new ServiceActionDescriptor("f013.quantity", List.of(new ActionField("quantity", VariableValueType.LONG, true, false)), List.of(new ActionField("recordedQuantity", VariableValueType.LONG, true, false))); }
        public ServiceActionResult execute(ServiceActionContext context, Map<String, WorkflowValue> inputs) { return new ServiceActionResult(Map.of("recordedQuantity", inputs.get("quantity"))); }
    }
}
