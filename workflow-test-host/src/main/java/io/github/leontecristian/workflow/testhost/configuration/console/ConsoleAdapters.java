package io.github.leontecristian.workflow.testhost.configuration.console;

import io.github.leontecristian.workflow.core.dto.definition.DefinitionCapabilities;
import io.github.leontecristian.workflow.core.dto.definition.SingleAssignmentCapability;
import io.github.leontecristian.workflow.core.dto.definition.TaskTypeCapability;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.service.definition.DefinitionCapabilityProvider;
import io.github.leontecristian.workflow.core.service.runtime.SingleTaskAssignmentResolver;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowEventRecorder;
import io.github.leontecristian.workflow.testhost.repository.event.ConsoleEventStore;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;
import io.github.leontecristian.workflow.testhost.service.event.ConsoleEventRecorder;
import io.github.leontecristian.workflow.testhost.service.runtime.SandboxSharedAssignmentProvider;
import io.github.leontecristian.workflow.testhost.service.runtime.SandboxSharedAssignmentResolver;
import io.github.leontecristian.workflow.testhost.service.sandbox.SandboxAssignmentResolver;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "workflow.console.enabled", havingValue = "true")
public class ConsoleAdapters {
    @Bean
    DefinitionCapabilityProvider consoleCapabilities(ConsoleSettings settings) {
        return () ->
                settings.sample
                        ? new DefinitionCapabilities(
                                Set.of(new SingleAssignmentCapability("sandbox.singleReviewer", Set.of(AssignmentTargetKind.USER)),
                                        new SingleAssignmentCapability("sandbox.sharedGroupReviewer", Set.of(AssignmentTargetKind.GROUP)),
                                        new SingleAssignmentCapability("sandbox.sharedRoleReviewer", Set.of(AssignmentTargetKind.ROLE))),
                                Set.of(
                                        new TaskTypeCapability(
                                                "SANDBOX_REVIEW", Set.of("COMPLETE", "APPROVE", "REJECT"))))
                        : new DefinitionCapabilities(Set.of(), Set.of());
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
    WorkflowEventRecorder consoleRecorder(
            ConsoleEventStore events, ConsoleWire wire, ConsoleSettings settings) {
        return new ConsoleEventRecorder(events, wire, settings);
    }
}
