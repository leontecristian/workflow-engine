package io.github.leontecristian.workflow.testhost;

import io.github.leontecristian.workflow.testhost.configuration.integration.DatabaseIntegrationTest;
import io.github.leontecristian.workflow.web.configuration.WorkflowAdministrationConfiguration;
import io.github.leontecristian.workflow.web.configuration.WorkflowAdministrationOptions;
import io.github.leontecristian.workflow.web.exception.administration.WorkflowAdministrationException;
import io.github.leontecristian.workflow.web.integration.administration.WorkflowAdministrationAuditRecorder;
import io.github.leontecristian.workflow.web.integration.administration.WorkflowAdministrationAuthorization;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = WorkflowTestHostApplication.class, properties = "workflow.console.enabled=false")
@Import(AdministrationWithoutAuditIT.AdministrationHost.class)
class AdministrationWithoutAuditIT extends DatabaseIntegrationTest {
    @Autowired WebApplicationContext web;
    @Autowired JdbcTemplate jdbc;

    private final String code = "F031_" + UUID.randomUUID().toString().replace("-", "");

    @Test void authorizedChangeCommitsWithoutRecorder() throws Exception {
        assertThat(web.getBeansOfType(WorkflowAdministrationAuditRecorder.class)).isEmpty();
        var mvc = MockMvcBuilders.webAppContextSetup(web).build();
        var create = post("/manage/task-types")
                .requestAttr("test.correlation", UUID.randomUUID().toString())
                .contentType("application/json")
                .content("""
                        {"code":"%s","name":"F031 task type","selectable":true}
                        """.formatted(code));

        authenticate("f031-reviewer");
        mvc.perform(create)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(taskTypeCount()).isZero();

        authenticate("f031-admin");
        mvc.perform(create)
                .andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(true));
        assertThat(taskTypeCount()).isEqualTo(1);
    }

    private static void authenticate(String actorRef) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(actorRef, "unused", List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private int taskTypeCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_task_type WHERE code=?", Integer.class, code);
    }

    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM wf.workflow_task_type WHERE code=?", code);
        assertThat(taskTypeCount()).isZero();
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Import(WorkflowAdministrationConfiguration.class)
    static class AdministrationHost {
        @Bean WorkflowAdministrationOptions options() {
            return new WorkflowAdministrationOptions("/manage", "test.correlation");
        }

        @Bean WorkflowAdministrationAuthorization authorization() {
            return actorRef -> {
                if (!actorRef.equals("f031-admin")) throw new WorkflowAdministrationException(403, "FORBIDDEN");
            };
        }
    }
}
