package io.github.leontecristian.workflow.testhost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.leontecristian.workflow.core.dto.runtime.InstanceStatus;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstancePage;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceQuery;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceSummary;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.testhost.controller.runtime.ConsoleRuntimeController;
import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;

/** Real method-security advisor, no application context/database/live HTTP. */
class ConsoleInstancesUnitTest {
    private final WorkflowRuntimeQueryService reads = mock(WorkflowRuntimeQueryService.class);
    private final ConsoleRuntimeController controller =
            new ConsoleRuntimeController(reads, null, null, null, null, null, new ConsoleWire(), null);

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void defaultRunningFilterAndLongWirePrecisionArePreserved() {
        long id = 9007199254740993L;
        var item =
                new WorkflowInstanceSummary(
                        id,
                        3,
                        "sandbox.review",
                        3,
                        InstanceStatus.ACTIVE,
                        "REVIEW",
                        null,
                        "SANDBOX_REQUEST",
                        "9",
                        Instant.EPOCH,
                        "admin",
                        null,
                        4,
                        1);
        when(reads.listInstances(any())).thenReturn(new WorkflowInstancePage(List.of(item), id));
        var response = controller.instances(new MockHttpServletRequest());
        verify(reads).listInstances(new WorkflowInstanceQuery(InstanceStatus.ACTIVE, 0, 50));
        assertTrue(response.getBody().contains("\"id\":\"9007199254740993\""));
        assertTrue(response.getBody().contains("\"nextAfterId\":\"9007199254740993\""));
    }

    @Test
    void explicitAllAndStatusFiltersKeepCursorAndLimit() {
        when(reads.listInstances(any())).thenReturn(new WorkflowInstancePage(List.of(), null));
        var all = new MockHttpServletRequest();
        all.addParameter("status", "ALL");
        all.addParameter("afterId", "9007199254740993");
        all.addParameter("limit", "1");
        controller.instances(all);
        verify(reads).listInstances(new WorkflowInstanceQuery(null, 9007199254740993L, 1));
        var completed = new MockHttpServletRequest();
        completed.addParameter("status", "COMPLETED");
        controller.instances(completed);
        verify(reads).listInstances(new WorkflowInstanceQuery(InstanceStatus.COMPLETED, 0, 50));
    }

    @Test
    void invalidOrDuplicateParametersAreRejectedBeforeCoreRead() {
        for (String value : new String[] {"active", "", "UNKNOWN"}) {
            var request = new MockHttpServletRequest();
            request.addParameter("status", value);
            assertEquals(
                    400,
                    assertThrows(ConsoleException.class, () -> controller.instances(request))
                            .status());
        }
        var duplicate = new MockHttpServletRequest();
        duplicate.addParameter("status", "ACTIVE", "ALL");
        assertThrows(ConsoleException.class, () -> controller.instances(duplicate));
        var unsupported = new MockHttpServletRequest();
        unsupported.addParameter("assignee", "reviewer");
        assertThrows(ConsoleException.class, () -> controller.instances(unsupported));
        verifyNoInteractions(reads);
    }

    @Test
    void reviewerCannotInvokeTheGlobalListButAdministratorCan() {
        var factory = new ProxyFactory(controller);
        factory.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        var secured = (ConsoleRuntimeController) factory.getProxy();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new TestingAuthenticationToken(
                                "reviewer", "unit-only", "ROLE_CONSOLE_REVIEWER"));
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> secured.instances(new MockHttpServletRequest()));
        verifyNoInteractions(reads);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new TestingAuthenticationToken("admin", "unit-only", "ROLE_CONSOLE_ADMIN"));
        when(reads.listInstances(any())).thenReturn(new WorkflowInstancePage(List.of(), null));
        assertEquals(200, secured.instances(new MockHttpServletRequest()).getStatusCode().value());
    }
}
