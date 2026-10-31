package io.github.leontecristian.workflow.testhost.dto.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditView(
        long id,
        Instant occurredAt,
        String actorRef,
        String operation,
        String entityKind,
        long entityId,
        UUID correlationId,
        String evidenceJson) {}
