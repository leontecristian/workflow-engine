package io.github.leontecristian.workflow.testhost;

import io.github.leontecristian.workflow.core.dto.tasktype.CreateTaskType;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleAdministrationConfiguration;
import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSecurity;
import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleErrors;
import io.github.leontecristian.workflow.testhost.service.definition.ConsoleManagement;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockServletContext;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.List;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real sandbox security filters + explicit shared administration activation, with core/transaction doubles only. */
class ConsoleAdministrationUnitTest {
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({ConsoleSecurity.class, ConsoleAdministrationConfiguration.class})
    static class Host {
        @Bean ConsoleSettings settings(Environment environment) { return new ConsoleSettings(environment); }
        @Bean ConsoleWire wire() { return new ConsoleWire(); }
        @Bean ConsoleErrors errors(ConsoleWire wire) { return new ConsoleErrors(wire); }
        @Bean ConsoleManagement management() { return mock(ConsoleManagement.class); }
        @Bean WorkflowDefinitionService definitions() { return mock(WorkflowDefinitionService.class); }
        @Bean TaskTypeCatalogService catalog() { return mock(TaskTypeCatalogService.class); }
    }
    private MockHttpSession session(String role) {
        var session = new MockHttpSession();
        var auth = UsernamePasswordAuthenticationToken.authenticated("unit-user", "unused",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, new SecurityContextImpl(auth));
        return session;
    }
    @Test void anonymousReviewerAndCsrfDenialsNeverReachMutationProxy() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                    "workflow.console.enabled=true", "workflow.console.admin.username=admin",
                    "workflow.console.admin.password=unit-only-password", "workflow.console.reviewer.username=reviewer",
                    "workflow.console.reviewer.password=unit-only-password");
            context.register(Host.class); context.refresh();
            var security = context.getBean("springSecurityFilterChain", jakarta.servlet.Filter.class);
            var mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
            var management = context.getBean(ConsoleManagement.class);
            mvc.perform(get("/console/api/definitions")).andExpect(status().isUnauthorized());
            mvc.perform(get("/console/api/definitions").session(session("CONSOLE_REVIEWER")))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
            var reviewer = session("CONSOLE_REVIEWER");
            var admin = session("CONSOLE_ADMIN");
            var csrfRepository = new HttpSessionCsrfTokenRepository();
            for (var session : List.of(reviewer, admin)) {
                var request = new MockHttpServletRequest(); request.setSession(session);
                var token = csrfRepository.generateToken(request);
                csrfRepository.saveToken(token, request, new MockHttpServletResponse());
                var write = post("/console/api/task-types").session(session).contentType("application/json")
                        .content("{\"code\":\"TEST\",\"name\":\"Test\",\"selectable\":true}");
                // Reviewer with valid CSRF tests the adapter; administrator with an invalid token tests host enforcement.
                write.header(token.getHeaderName(), session == reviewer ? token.getToken() : "invalid-token");
                mvc.perform(write).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
            }
            verifyNoInteractions(management, context.getBean(WorkflowDefinitionService.class), context.getBean(TaskTypeCatalogService.class));
            var request = new MockHttpServletRequest(); request.setSession(admin);
            var token = csrfRepository.loadToken(request);
            mvc.perform(post("/console/api/task-types").session(admin).header(token.getHeaderName(), token.getToken())
                    .contentType("application/json").content("{\"code\":\"TEST\",\"name\":\"Test\",\"selectable\":true}"))
                    .andExpect(status().isOk());
            verify(management).createTaskType(eq(new CreateTaskType("TEST", "Test", null, true)), matches("[a-f0-9-]{36}"));
            verifyNoMoreInteractions(management);
        }
    }
}
