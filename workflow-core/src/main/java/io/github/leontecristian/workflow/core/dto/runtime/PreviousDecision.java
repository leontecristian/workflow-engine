package io.github.leontecristian.workflow.core.dto.runtime;

import java.time.Instant;

/** Narrow immutable decision attribution; source IDs confer no source-task access. */
public record PreviousDecision(long taskId, long activityId, String nodeKey, String nodeName,
        String outcomeKey, String outcomeLabel, ActorKind actorKind, String actorRef,
        Instant occurredAt, long historySequence, String comment) { }
