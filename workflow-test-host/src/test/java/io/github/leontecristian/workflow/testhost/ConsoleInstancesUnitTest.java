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
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeReadHost;
import io.github.leontecristian.workflow.web.controller.runtime.WorkflowRuntimeReadController;
import io.github.leontecristian.workflow.web.exception.runtime.WorkflowRuntimeException;
import io.github.leontecristian.workflow.web.service.runtime.RuntimeWire;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Real sandbox role policy through the extracted host adapter; no database/live HTTP. */
class ConsoleInstancesUnitTest {
    private final WorkflowRuntimeQueryService reads = mock(WorkflowRuntimeQueryService.class);
    private final ConsoleContext context = new ConsoleContext(java.time.Clock.systemUTC());
    private final WorkflowRuntimeReadController controller = new WorkflowRuntimeReadController(
            reads, null, null, new ConsoleRuntimeReadHost(reads, null, context), new RuntimeWire(), null, false);

    @BeforeEach
    void administrator() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin", "unit-only", "ROLE_CONSOLE_ADMIN"));
    }

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
                    assertThrows(WorkflowRuntimeException.class, () -> controller.instances(request))
                            .status());
        }
        var duplicate = new MockHttpServletRequest();
        duplicate.addParameter("status", "ACTIVE", "ALL");
        assertThrows(WorkflowRuntimeException.class, () -> controller.instances(duplicate));
        var unsupported = new MockHttpServletRequest();
        unsupported.addParameter("assignee", "reviewer");
        assertThrows(WorkflowRuntimeException.class, () -> controller.instances(unsupported));
        verifyNoInteractions(reads);
    }

    @Test
    void reviewerCannotInvokeTheGlobalListButAdministratorCan() {
        var secured = controller;
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new TestingAuthenticationToken(
                                "reviewer", "unit-only", "ROLE_CONSOLE_REVIEWER"));
        assertThrows(
                WorkflowRuntimeException.class,
                () -> secured.instances(new MockHttpServletRequest()));
        var malformed = new MockHttpServletRequest();
        malformed.addParameter("unknown", "1");
        assertEquals(403, assertThrows(WorkflowRuntimeException.class, () -> secured.instances(malformed)).status());
        verifyNoInteractions(reads);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new TestingAuthenticationToken("admin", "unit-only", "ROLE_CONSOLE_ADMIN"));
        when(reads.listInstances(any())).thenReturn(new WorkflowInstancePage(List.of(), null));
        assertEquals(200, secured.instances(new MockHttpServletRequest()).getStatusCode().value());
    }
}
