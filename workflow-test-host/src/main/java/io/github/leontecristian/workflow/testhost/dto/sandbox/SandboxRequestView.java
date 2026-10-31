package io.github.leontecristian.workflow.testhost.dto.sandbox;

import java.time.Instant;

public record SandboxRequestView(
        long id,
        String name,
        String createdBy,
        Instant createdAt,
        long lockVersion,
        Long instanceId) {}
