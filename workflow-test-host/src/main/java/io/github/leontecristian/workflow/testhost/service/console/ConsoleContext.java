package io.github.leontecristian.workflow.testhost.service.console;

import io.github.leontecristian.workflow.core.dto.definition.DefinitionManagementContext;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ConsoleContext {
    private final java.time.Clock clock;

    public ConsoleContext(java.time.Clock clock) {
        this.clock = clock;
    }

    public String actor() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    public boolean isConsoleActor(String actorRef) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getName().equals(actorRef)
                && authentication.getAuthorities().stream().anyMatch(authority ->
                        "ROLE_CONSOLE_ADMIN".equals(authority.getAuthority())
                                || "ROLE_CONSOLE_REVIEWER".equals(authority.getAuthority()));
    }

    public boolean admin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CONSOLE_ADMIN"));
    }

    public Instant now() {
        Instant time = clock.instant();
        return Instant.ofEpochSecond(time.getEpochSecond(), time.getNano() / 100 * 100L);
    }

    public DefinitionManagementContext management() {
        return new DefinitionManagementContext(actor(), now());
    }

    public WorkflowCommandContext runtime(String correlation) {
        return new WorkflowCommandContext(ActorKind.USER, actor(), now(), correlation);
    }
}
