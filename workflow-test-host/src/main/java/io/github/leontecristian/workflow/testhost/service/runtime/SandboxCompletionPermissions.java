package io.github.leontecristian.workflow.testhost.service.runtime;

import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings;
import io.github.leontecristian.workflow.testhost.dto.sandbox.SandboxRequestView;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Bounded local permission fixture; these roles are independent of workflow assignments. */
@Component
public class SandboxCompletionPermissions {
    public enum Resource { BOOKING }
    public enum Scope { VIEW, DECIDE }
    private enum Role { VIEWER, DECIDER }
    private enum Context { PRIMARY, SECONDARY }
    private static final Map<Role, Set<Scope>> GRANTS = Map.of(
            Role.VIEWER, Set.of(Scope.VIEW), Role.DECIDER, Set.of(Scope.VIEW, Scope.DECIDE));
    private final Map<Context, Map<String, Role>> memberships;

    public SandboxCompletionPermissions(ConsoleSettings settings) {
        var primary = new java.util.HashMap<String, Role>();
        var secondary = new java.util.HashMap<String, Role>();
        if (settings.admin != null) { primary.put(settings.admin, Role.VIEWER); secondary.put(settings.admin, Role.VIEWER); }
        if (settings.reviewer != null) { primary.put(settings.reviewer, Role.DECIDER); secondary.put(settings.reviewer, Role.VIEWER); }
        if (settings.reviewer2 != null) { primary.put(settings.reviewer2, Role.DECIDER); secondary.put(settings.reviewer2, Role.DECIDER); }
        memberships = Map.of(Context.PRIMARY, Map.copyOf(primary), Context.SECONDARY, Map.copyOf(secondary));
    }

    public boolean isAllowed(String actor, Resource resource, Scope scope, SandboxRequestView request) {
        var context = request.name().startsWith("F028-SECONDARY:") ? Context.SECONDARY : Context.PRIMARY;
        var role = memberships.get(context).get(actor);
        return resource == Resource.BOOKING && role != null && GRANTS.get(role).contains(scope);
    }
}
