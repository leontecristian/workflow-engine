package io.github.leontecristian.workflow.web.dto.instance;

/** A null task identifies an activity grant; a nonnull task grants only that exact task. */
public record ViewerLinkReference(long activityId, Long taskId) {}
