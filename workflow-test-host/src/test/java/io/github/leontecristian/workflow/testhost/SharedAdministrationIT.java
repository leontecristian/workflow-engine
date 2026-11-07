package io.github.leontecristian.workflow.testhost;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionManagementContext;
import io.github.leontecristian.workflow.core.dto.definition.ImportWorkflowDraft;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.testhost.configuration.integration.DatabaseIntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** One changed route/audit boundary; optional retained draft for the focused browser check. */
@SpringBootTest(classes = WorkflowTestHostApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"workflow.console.enabled=true", "workflow.console.capabilities=none",
                "workflow.console.admin.username=f014-admin", "workflow.console.admin.password=f014-local-test-password",
                "workflow.console.reviewer.username=f014-reviewer", "workflow.console.reviewer.password=f014-local-test-password"})
class SharedAdministrationIT extends DatabaseIntegrationTest {
    @Autowired WorkflowDefinitionService definitions;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired WebApplicationContext web;
    @LocalServerPort int port;
    private final String code = "F014_" + UUID.randomUUID().toString().replace("-", "");
    private final String key = "f014." + UUID.randomUUID();
    private final ObjectMapper json = new ObjectMapper();
    private Path repository() { return Files.isDirectory(Path.of("docs")) ? Path.of("") : Path.of(".."); }

    @Test void extractedWriteCommitsWithHostAudit() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(web).addFilters(web.getBean(FilterChainProxy.class)).build();
        var session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(
                UsernamePasswordAuthenticationToken.authenticated("f014-admin", "unused",
                        List.of(new SimpleGrantedAuthority("ROLE_CONSOLE_ADMIN")))));
        var sessionResponse = mvc.perform(get("/console/api/session").session(session)).andReturn().getResponse();
        assertThat(sessionResponse.getStatus()).isEqualTo(200);
        var csrf = json.readTree(sessionResponse.getContentAsString());
        var response = mvc.perform(post("/console/api/task-types").session(session)
                .header(csrf.path("csrfHeaderName").asText(), csrf.path("csrfToken").asText())
                .contentType("application/json").content(json.writeValueAsString(
                        Map.of("code", code, "name", "F014 administration fixture", "selectable", true))))
                .andReturn().getResponse();
        assertThat(response.getStatus()).withFailMessage(response.getContentAsString()).isEqualTo(200);
        var change = json.readTree(response.getContentAsString());
        assertThat(change.path("changed").asBoolean()).isTrue();
        assertThat(change.path("after").path("id").isTextual()).isTrue();
        long taskType = Long.parseLong(change.path("after").path("id").asText());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_task_type WHERE id=? AND code=?",
                Integer.class, taskType, code)).isEqualTo(1);
        var evidence = jdbc.queryForObject("SELECT evidence_json FROM wf_test.management_audit"
                        + " WHERE entity_kind='TASK_TYPE' AND entity_id=? AND operation='CREATE_TASK_TYPE' AND actor_ref='f014-admin'",
                String.class, taskType);
        assertThat(json.readTree(evidence).path("after").path("code").asText()).isEqualTo(code);

        if (Boolean.getBoolean("f014.browserHold")) {
            var document = (ObjectNode) json.readTree(Files.readString(repository().resolve(
                    "docs/features/F011-sequential-human-stages/sequential-review.json")));
            document.put("key", key);
            document.put("name", "F014 browser draft");
            for (var node : document.path("nodes")) {
                if (node.path("type").asText().equals("HUMAN_TASK")) {
                    ((ObjectNode) node).put("taskType", code);
                }
            }
            var draft = new TransactionTemplate(transactions).execute(status -> definitions.importDraft(
                    new ImportWorkflowDraft(document.toString()), new DefinitionManagementContext("f014-admin", Instant.now())).after());
            Path local = repository().resolve(".local");
            Files.createDirectories(local);
            Files.writeString(local.resolve("f014-runtime.json"), json.writeValueAsString(Map.of(
                    "port", port, "pid", ProcessHandle.current().pid(), "version", draft.id(),
                    "family", draft.definitionId(), "taskType", taskType, "code", code)));
            Path done = local.resolve("f014-browser.done");
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(15);
            while (!Files.exists(done) && System.nanoTime() < deadline) Thread.sleep(250);
            assertThat(Files.exists(done)).as("Focused browser completion signal").isTrue();
            Files.deleteIfExists(done);
        }
    }

    @AfterEach void cleanup() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.update("DELETE FROM wf_test.management_audit WHERE actor_ref='f014-admin' AND ((entity_kind='TASK_TYPE'"
                    + " AND entity_id IN (SELECT id FROM wf.workflow_task_type WHERE code=?)) OR (entity_kind='DEFINITION_VERSION'"
                    + " AND entity_id IN (SELECT v.id FROM wf.workflow_definition_version v JOIN wf.workflow_definition d ON d.id=v.definition_id WHERE d.workflow_key=?))"
                    + " OR (entity_kind='DEFINITION' AND entity_id IN (SELECT id FROM wf.workflow_definition WHERE workflow_key=?)))", code, key, key);
            jdbc.update("DELETE FROM wf.workflow_definition_version WHERE definition_id IN (SELECT id FROM wf.workflow_definition WHERE workflow_key=?)", key);
            jdbc.update("DELETE FROM wf.workflow_definition WHERE workflow_key=?", key);
            jdbc.update("DELETE FROM wf.workflow_task_type WHERE code=?", code);
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_task_type WHERE code=?", Integer.class, code)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_definition WHERE workflow_key=?", Integer.class, key)).isZero();
    }
}
