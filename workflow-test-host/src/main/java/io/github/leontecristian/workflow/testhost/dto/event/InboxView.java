package io.github.leontecristian.workflow.testhost.dto.event;

import java.time.Instant;

public record InboxView(long id, String eventId, Instant receivedAt, String eventJson) {}
