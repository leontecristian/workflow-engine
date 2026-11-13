package io.github.leontecristian.workflow.core.repository.runtime;

import io.github.leontecristian.workflow.core.entity.runtime.WorkflowHistoryEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowTaskEntity;

/** Internal bounded evidence row, never a host response. */
public record PreviousDecisionRow(WorkflowTaskEntity task, WorkflowHistoryEntity fact) { }
