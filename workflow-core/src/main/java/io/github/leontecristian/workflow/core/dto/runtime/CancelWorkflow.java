package io.github.leontecristian.workflow.core.dto.runtime;

import java.util.UUID;

/** Immediate whole-instance stop; reason is retained exactly. */
public record CancelWorkflow(UUID commandId, long instanceId, long expectedLockVersion, String reason) { }
