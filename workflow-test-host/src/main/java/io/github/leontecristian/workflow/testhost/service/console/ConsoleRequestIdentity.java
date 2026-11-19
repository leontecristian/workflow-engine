package io.github.leontecristian.workflow.testhost.service.console;

import io.github.leontecristian.workflow.web.exception.WorkflowUnauthenticatedException;
import io.github.leontecristian.workflow.web.integration.WorkflowRequestIdentity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ConsoleRequestIdentity implements WorkflowRequestIdentity {
    @Override
    public String requireActorRef() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new WorkflowUnauthenticatedException("Console authentication is required");
        }
        return authentication.getName();
    }
}
