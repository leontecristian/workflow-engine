package io.github.leontecristian.workflow.core.dto.runtime;

import java.util.UUID;

/** Ownership command for an exact task under an expected aggregate version. */
public record ReleaseWorkflowTask(UUID commandId, long instanceId, long taskId, long expectedLockVersion) { }
