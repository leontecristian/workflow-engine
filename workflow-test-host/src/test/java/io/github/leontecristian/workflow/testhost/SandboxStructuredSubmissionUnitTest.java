package io.github.leontecristian.workflow.testhost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionContext;
import io.github.leontecristian.workflow.core.dto.runtime.TaskSubmission;
import io.github.leontecristian.workflow.core.dto.runtime.TaskSubmissionPage;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxServiceEffectStore;
import io.github.leontecristian.workflow.testhost.service.sandbox.SandboxRecordApprovedAmountAction;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

class SandboxStructuredSubmissionUnitTest {
    @Test
    void readsTheExactCompletedActivityAndRecordsCentsBeforeTheConfiguredFailure() {
        var beans = new DefaultListableBeanFactory();
        var reads = mock(WorkflowRuntimeQueryService.class);
        var effects = mock(SandboxServiceEffectStore.class);
        beans.registerSingleton("reads", reads);
        var provider = beans.getBeanProvider(WorkflowRuntimeQueryService.class);
        var time = Instant.parse("2026-01-01T00:00:00Z");
        var actor = new WorkflowCommandContext(ActorKind.USER, "reviewer", time, "sample");
        var command = UUID.randomUUID();
        var result = CompleteWorkflowTask.parseResult("{\"approvedAmount\":1250.00,\"note\":\"Approved\"}");
        var submission = new TaskSubmission(4, 3, 2, 10, command, actor.actorKind(), actor.actorRef(), time,
                "COMPLETE", null, Map.of(), result);
        when(reads.listActivitySubmissions(3, 0, 2)).thenReturn(new TaskSubmissionPage(List.of(submission), null));

        var regular = new SandboxRecordApprovedAmountAction(provider, effects, new ConsoleSettings(new MockEnvironment()));
        var completed = new ServiceActionContext(command, 2, 1, "recordAmount", "SANDBOX_REQUEST", "5", actor, 3L);
        assertThat(regular.execute(completed, Map.of()).outputs()).isEmpty();
        verify(effects).insert(completed, 125000);

        var settings = new ConsoleSettings(new MockEnvironment()
                .withProperty("workflow.console.enabled", "true")
                .withProperty("workflow.console.admin.username", "admin")
                .withProperty("workflow.console.admin.password", "test-admin")
                .withProperty("workflow.console.reviewer.username", "reviewer")
                .withProperty("workflow.console.reviewer.password", "test-reviewer")
                .withProperty("workflow.console.fail-service-node", "failRecordAmount"));
        var failing = new SandboxRecordApprovedAmountAction(provider, effects, settings);
        var failure = new ServiceActionContext(command, 2, 1, "failRecordAmount", "SANDBOX_REQUEST", "5", actor, 3L);
        assertThatThrownBy(() -> failing.execute(failure, Map.of())).isInstanceOf(IllegalStateException.class)
                .hasMessage("Configured service failure");
        verify(effects).insert(failure, 125000);
    }
}
