package io.github.leontecristian.workflow.testhost.configuration.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import io.github.leontecristian.workflow.core.dto.runtime.WorkflowAuthorizationRequest;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowScope;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowTaskCompletionValidator;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleWorkflowAuthorization;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** Explicit completion policy doubles for existing engine fixtures; F028 exercises real host policy. */
public abstract class CompletionAdapterIntegrationTest extends DatabaseIntegrationTest {
    @MockitoSpyBean protected ConsoleWorkflowAuthorization authorization;
    @MockitoBean protected WorkflowTaskCompletionValidator completionValidator;

    @BeforeEach void configureCompletionFixturePolicy() {
        doReturn(true).when(authorization).isAllowed(forScope(WorkflowScope.ACCESS_COMPLETION));
        doReturn(true).when(authorization).isAllowed(forScope(WorkflowScope.COMPLETE));
        doNothing().when(completionValidator).validate(any(), org.mockito.ArgumentMatchers.nullable(
                io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue.class));
    }

    protected static WorkflowAuthorizationRequest forScope(WorkflowScope scope) {
        return argThat(request -> request != null && request.permission().scope() == scope);
    }
}
