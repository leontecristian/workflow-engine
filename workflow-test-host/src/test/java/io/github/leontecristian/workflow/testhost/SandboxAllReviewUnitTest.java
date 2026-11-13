package io.github.leontecristian.workflow.testhost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionContext;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxServiceEffectStore;
import io.github.leontecristian.workflow.testhost.service.sandbox.SandboxRecordReviewCollectionAction;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SandboxAllReviewUnitTest {
    @Test
    void recordsOriginalContributionCountWithoutConvertingExactResultNumbers() {
        var effects = mock(SandboxServiceEffectStore.class);
        var action = new SandboxRecordReviewCollectionAction(effects, new ConsoleSettings(new MockEnvironment()));
        var context = new ServiceActionContext(UUID.randomUUID(), 2, 1, "recordApproved", "SANDBOX_REQUEST", "5",
                new WorkflowCommandContext(ActorKind.USER, "reviewer", Instant.EPOCH, "sample"));
        var reviews = WorkflowJsonValue.parse("[{\"taskId\":\"41\",\"taskKey\":\"DIRECT\",\"outcomeKey\":\"APPROVE\",\"comment\":null,\"result\":{\"amount\":9007199254740993.00}},{\"taskId\":\"42\",\"taskKey\":\"FINANCE\",\"outcomeKey\":\"REJECT\",\"comment\":\"Missing agreement\"}]");
        assertThat(action.execute(context, Map.of("reviews", new WorkflowValue(VariableValueType.JSON, reviews))).outputs()).isEmpty();
        verify(effects).insert(context, 2);
        assertThat(reviews.toJson()).contains("9007199254740993.00");
    }

    @Test
    void rejectsNonCollectionBeforeRecordingAnEffect() {
        var effects = mock(SandboxServiceEffectStore.class);
        var action = new SandboxRecordReviewCollectionAction(effects, new ConsoleSettings(new MockEnvironment()));
        assertThatThrownBy(() -> action.execute(null, Map.of("reviews",
                new WorkflowValue(VariableValueType.JSON, WorkflowJsonValue.parse("{}")))))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(effects);
    }
}
