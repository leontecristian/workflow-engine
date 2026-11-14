package io.github.leontecristian.workflow.testhost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.leontecristian.workflow.core.dto.runtime.StartInputs;
import io.github.leontecristian.workflow.testhost.controller.runtime.ConsoleRuntimeController;
import io.github.leontecristian.workflow.testhost.dto.runtime.SandboxStart;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxServiceEffectStore;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeAccess;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeCoordinator;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeTransaction;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.support.TestPropertySourceUtils;

/** Method-security proxy with isolated collaborators; no host, database or HTTP server starts. */
class ConsoleStartAuthorizationUnitTest {
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class SecurityOnly {}

    @Test
    void startAndReplayRequireAdministrationBeforeParsingEverySelectorForm() {
        var previous = SecurityContextHolder.getContext();
        var commands = mock(ConsoleRuntimeCoordinator.class);
        var transactions = mock(ConsoleRuntimeTransaction.class);
        var context = mock(ConsoleContext.class);
        try (var application = new AnnotationConfigApplicationContext()) {
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(application, "workflow.console.enabled=true");
            application.register(SecurityOnly.class);
            application.registerBean(ConsoleRuntimeController.class, () -> new ConsoleRuntimeController(
                    mock(ConsoleRuntimeAccess.class), commands, transactions, context, new ConsoleWire(),
                    mock(SandboxServiceEffectStore.class)));
            application.refresh();
            var controller = application.getBean(ConsoleRuntimeController.class);
            var unreadRequest = mock(HttpServletRequest.class);

            SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
            assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controller.start(unreadRequest));
            assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controller.replayStart(unreadRequest));
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    "reviewer", "unused", List.of(new SimpleGrantedAuthority("ROLE_CONSOLE_REVIEWER"))));
            assertThrows(AccessDeniedException.class, () -> controller.start(unreadRequest));
            assertThrows(AccessDeniedException.class, () -> controller.replayStart(unreadRequest));
            verifyNoInteractions(unreadRequest, commands, transactions, context);

            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    "admin", "unused", List.of(new SimpleGrantedAuthority("ROLE_CONSOLE_ADMIN"))));
            when(transactions.replayStart(any(), isNull())).thenReturn(Optional.empty());
            var commandId = UUID.fromString("9a839746-ac19-47fe-985b-a7fdd0091a52");
            var bodies = List.of("\"workflowKey\":\"booking.review\"", "\"definitionId\":\"17\"",
                    "\"definitionVersionId\":\"29\"");
            var expected = List.of(
                    new SandboxStart(commandId, "booking.review", null, null, 123, 0, StartInputs.empty()),
                    new SandboxStart(commandId, null, 17L, null, 123, 0, StartInputs.empty()),
                    new SandboxStart(commandId, 29, 123, 0));
            for (int i = 0; i < bodies.size(); i++) {
                assertEquals(200, controller.start(request(commandId, bodies.get(i))).getStatusCode().value());
                assertEquals(200, controller.replayStart(request(commandId, bodies.get(i))).getStatusCode().value());
                verify(commands).start(expected.get(i), null);
                verify(transactions).replayStart(expected.get(i), null);
            }
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private MockHttpServletRequest request(UUID commandId, String selector) {
        var request = new MockHttpServletRequest();
        request.setContentType("application/json");
        request.setContent(("{\"commandId\":\"" + commandId + "\"," + selector
                + ",\"requestId\":\"123\",\"expectedRequestVersion\":\"0\",\"inputs\":{}}")
                .getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
