package io.github.leontecristian.workflow.core.repository.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.TaskClosureReason;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import java.time.LocalDateTime;

/** Scalar task-page projection includes ownership without per-item activity queries. */
public interface RuntimeTaskRow {
    Long getId();
    Long getActivityId();
    Long getInstanceId();
    Long getTaskTypeId();
    String getTaskKey();
    String getAssignedUserRef();
    TaskStatus getStatus();
    String getOutcomeKey();
    LocalDateTime getCreatedAt();
    LocalDateTime getDueAt();
    LocalDateTime getClosedAt();
    String getClosedBy();
    TaskClosureReason getClosureReason();
}
