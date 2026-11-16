package io.github.leontecristian.workflow.testhost.configuration.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.github.leontecristian.workflow.core.service.runtime.WorkflowTaskCompletionAuthorization;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowTaskCompletionValidator;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Explicit completion policy doubles for existing engine fixtures; F028 exercises real host adapters. */
public abstract class CompletionAdapterIntegrationTest extends DatabaseIntegrationTest {
    @MockitoBean protected WorkflowTaskCompletionAuthorization completionAuthorization;
    @MockitoBean protected WorkflowTaskCompletionValidator completionValidator;

    @BeforeEach void configureCompletionFixturePolicy() {
        // These suites own engine/other-operation scenarios, without a console authentication session.
        when(completionAuthorization.canAccess(any())).thenReturn(true);
        when(completionAuthorization.canComplete(any())).thenReturn(true);
        doNothing().when(completionValidator).validate(any(), org.mockito.ArgumentMatchers.nullable(
                io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue.class));
    }
}
