package io.github.leontecristian.workflow.core.dto.runtime;

import java.time.Instant;

/** Exclusive position in descending task creation time and numeric task ID order. */
public record WorkflowPersonalTaskCursor(Instant createdAt, long taskId) {
}
