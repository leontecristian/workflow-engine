package io.github.leontecristian.workflow.core.repository.runtime;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import java.time.LocalDateTime;
import java.util.UUID;
/** SQL scalar projection excludes the potentially large history details column. */
public interface RuntimeHistoryRow {
    Long getInstanceId();
    Long getHistorySequence();
    String getEventType();
    UUID getCommandId();
    String getCorrelationId();
    ActorKind getActorKind();
    String getActorRef();
    LocalDateTime getOccurredAt();
    Long getActivityId();
    Long getTaskId();
    String getSourceNodeKey();
    String getTargetNodeKey();
    String getTransitionKey();
    Short getDetailsSchemaVersion();
}
