package io.github.leontecristian.workflow.testhost.dto.sandbox;

import java.time.Instant;

/** Bounded collection projection; JSON payloads are available only on exact reads. */
public record SandboxRequestSummary(
        long id, String name, String status, String createdBy, Instant createdAt,
        long lockVersion, Long instanceId) {}
