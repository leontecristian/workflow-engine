package io.github.leontecristian.workflow.testhost.service.administration;

import io.github.leontecristian.workflow.web.integration.administration.WorkflowAdministrationAuthorization;
import io.github.leontecristian.workflow.web.integration.administration.WorkflowAdministrationAuditRecorder;
import io.github.leontecristian.workflow.web.dto.administration.WorkflowAdministrationAudit;
import io.github.leontecristian.workflow.web.exception.administration.WorkflowAdministrationException;
import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings;
import io.github.leontecristian.workflow.testhost.repository.audit.ManagementAuditStore;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Sandbox administration policy and transactional audit storage. */
public class ConsoleAdministrationHost implements WorkflowAdministrationAuthorization, WorkflowAdministrationAuditRecorder {
    private final ManagementAuditStore audits;
    private final ConsoleWire wire;
    private final ConsoleSettings settings;

    public ConsoleAdministrationHost(ManagementAuditStore audits, ConsoleWire wire, ConsoleSettings settings) {
        this.audits = audits;
        this.wire = wire;
        this.settings = settings;
    }

    @Override public void requireAdministrationAccess(String actorRef) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.getName().equals(actorRef)
                || authentication.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_CONSOLE_ADMIN")))
            throw new WorkflowAdministrationException(403, "FORBIDDEN");
    }

    @Override public void record(WorkflowAdministrationAudit audit) {
        if (settings.failAudit) throw new IllegalStateException("Configured audit failure");
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("schemaVersion", 1);
        evidence.put("operation", audit.operation());
        evidence.put("before", audit.before());
        evidence.put("after", audit.after());
        String json = wire.json(evidence);
        if (json.length() * 2L > 512 * 1024)
            throw new IllegalStateException("Audit evidence exceeds bound");
        audits.insert(audit.context().occurredAt(), audit.context().actorRef(), audit.operation(),
                audit.entityKind(), audit.entityId(), UUID.fromString(audit.correlationId()), json);
    }
}
