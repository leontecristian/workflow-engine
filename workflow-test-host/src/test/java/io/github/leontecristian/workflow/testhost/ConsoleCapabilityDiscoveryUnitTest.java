package io.github.leontecristian.workflow.testhost;

import io.github.leontecristian.workflow.core.dto.definition.TaskTypeCapability;
import io.github.leontecristian.workflow.core.service.runtime.AllTaskAssignmentResolver;
import io.github.leontecristian.workflow.core.service.runtime.SingleTaskAssignmentResolver;
import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleAdapters;
import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings;
import io.github.leontecristian.workflow.testhost.repository.event.ConsoleEventStore;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ConsoleCapabilityDiscoveryUnitTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(ConsoleAdapters.class, ConsoleSettings.class)
            .withBean(ConsoleEventStore.class, () -> mock(ConsoleEventStore.class))
            .withBean(SandboxRequestStore.class, () -> mock(SandboxRequestStore.class))
            .withBean(ConsoleWire.class)
            .withPropertyValues("workflow.console.enabled=true",
                    "workflow.console.admin.username=administrator", "workflow.console.admin.password=fixture",
                    "workflow.console.reviewer.username=reviewer", "workflow.console.reviewer.password=fixture");

    @Test void discoversSampleTaskCapabilitiesAndBothResolverPoliciesByDefault() {
        context.run(application -> {
            assertThat(application.getBeansOfType(TaskTypeCapability.class).values())
                    .containsExactlyInAnyOrder(
                            new TaskTypeCapability("SANDBOX_REVIEW", Set.of("COMPLETE", "APPROVE", "REJECT")),
                            new TaskTypeCapability("BOOKING_REVIEW", Set.of("APPROVE", "REJECT")));
            assertThat(application.getBeansOfType(SingleTaskAssignmentResolver.class).values())
                    .extracting(SingleTaskAssignmentResolver::identifier)
                    .containsExactlyInAnyOrder("sandbox.singleReviewer", "sandbox.sharedGroupReviewer", "sandbox.sharedRoleReviewer");
            assertThat(application.getBeansOfType(AllTaskAssignmentResolver.class).values())
                    .extracting(AllTaskAssignmentResolver::identifier).containsExactly("sandbox.allReviewers");
        });
    }

    @Test void sampleDisabledOmitsTaskCapabilitiesAndResolvers() {
        context.withPropertyValues("workflow.console.capabilities=none").run(application -> {
            assertThat(application).hasNotFailed();
            assertThat(application).doesNotHaveBean(TaskTypeCapability.class);
            assertThat(application).doesNotHaveBean(SingleTaskAssignmentResolver.class);
            assertThat(application).doesNotHaveBean(AllTaskAssignmentResolver.class);
        });
    }
}
