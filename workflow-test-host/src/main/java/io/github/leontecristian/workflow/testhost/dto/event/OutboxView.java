package io.github.leontecristian.workflow.testhost.dto.event;

import java.time.Instant;
import java.util.UUID;

public record OutboxView(
        long id,
        String eventId,
        long instanceId,
        long historySequence,
        UUID commandId,
        String eventType,
        Instant occurredAt,
        Instant deliveredAt,
        long deliveryCount,
        String eventJson) {}
