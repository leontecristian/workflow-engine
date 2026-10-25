package io.github.leontecristian.workflow.core.dto.tasktype;

/** Ascending ID cursor query; afterId >= 0 and limit is 1 through 100. */
public record TaskTypeQuery(TaskTypeFilter filter, long afterId, int limit) {
}
