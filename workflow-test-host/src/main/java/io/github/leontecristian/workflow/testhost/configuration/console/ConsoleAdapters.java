package io.github.leontecristian.workflow.testhost.configuration.console;

import io.github.leontecristian.workflow.core.dto.definition.TaskTypeCapability;
import io.github.leontecristian.workflow.core.service.runtime.SingleTaskAssignmentResolver;
import io.github.leontecristian.workflow.core.service.runtime.AllTaskAssignmentResolver;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowEventBatchWriter;
import io.github.leontecristian.workflow.testhost.repository.event.ConsoleEventStore;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;
import io.github.leontecristian.workflow.testhost.service.event.ConsoleEventBatchWriter;
import io.github.leontecristian.workflow.testhost.service.runtime.SandboxSharedAssignmentProvider;
import io.github.leontecristian.workflow.testhost.service.runtime.SandboxSharedAssignmentResolver;
import io.github.leontecristian.workflow.testhost.service.sandbox.SandboxAssignmentResolver;
import io.github.leontecristian.workflow.testhost.service.sandbox.SandboxAllAssignmentResolver;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "workflow.console.enabled", havingValue = "true")
public class ConsoleAdapters {
    @Bean
    @ConditionalOnProperty(name = "workflow.console.capabilities", havingValue = "sample", matchIfMissing = true)
    TaskTypeCapability sandboxReviewCapability() {
        return new TaskTypeCapability("SANDBOX_REVIEW", Set.of("COMPLETE", "APPROVE", "REJECT"));
    }

    @Bean
    @ConditionalOnProperty(name = "workflow.console.capabilities", havingValue = "sample", matchIfMissing = true)
    TaskTypeCapability bookingReviewCapability() {
        return new TaskTypeCapability("BOOKING_REVIEW", Set.of("APPROVE", "REJECT"));
    }

    @Bean
    @ConditionalOnProperty(
            name = "workflow.console.capabilities",
            havingValue = "sample",
            matchIfMissing = true)
    SingleTaskAssignmentResolver sandboxResolver(
            SandboxRequestStore requests, ConsoleSettings settings) {
        return new SandboxAssignmentResolver(requests, settings);
    }

    @Bean
    SandboxSharedAssignmentProvider sandboxMembership(ConsoleSettings settings) {
        return new SandboxSharedAssignmentProvider(Set.of(settings.reviewer, settings.reviewer2));
    }

    @Bean
    @ConditionalOnProperty(name = "workflow.console.capabilities", havingValue = "sample", matchIfMissing = true)
    AllTaskAssignmentResolver sandboxAllResolver(SandboxRequestStore requests, ConsoleSettings settings) {
        return new SandboxAllAssignmentResolver(requests, settings);
    }

    @Bean
    @ConditionalOnProperty(name = "workflow.console.capabilities", havingValue = "sample", matchIfMissing = true)
    SingleTaskAssignmentResolver sandboxGroupResolver(SandboxRequestStore requests) {
        return new SandboxSharedAssignmentResolver(
                "sandbox.sharedGroupReviewer", SandboxSharedAssignmentProvider.FINANCE, requests);
    }

    @Bean
    @ConditionalOnProperty(name = "workflow.console.capabilities", havingValue = "sample", matchIfMissing = true)
    SingleTaskAssignmentResolver sandboxRoleResolver(SandboxRequestStore requests) {
        return new SandboxSharedAssignmentResolver(
                "sandbox.sharedRoleReviewer", SandboxSharedAssignmentProvider.APPROVER, requests);
    }

    @Bean
    WorkflowEventBatchWriter consoleEventBatchWriter(
            ConsoleEventStore events, ConsoleWire wire, ConsoleSettings settings) {
        return new ConsoleEventBatchWriter(events, wire, settings);
    }
}
