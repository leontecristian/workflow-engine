package io.github.leontecristian.workflow.core.repository.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.InstanceStatus;

import java.time.LocalDateTime;

/** Internal scalar projection: one bounded query, without definition JSON or per-row lookups. */
public interface RuntimeInstanceRow {
    Long getId();

    Long getDefinitionVersionId();

    String getDefinitionKey();

    Integer getDefinitionVersion();

    InstanceStatus getStatus();

    String getCurrentNodeKey();

    String getResultKey();

    String getBusinessType();

    String getBusinessId();

    LocalDateTime getStartedAt();

    String getStartedBy();

    LocalDateTime getEndedAt();

    Long getLastHistorySequence();

    Long getLockVersion();
}
