package io.github.leontecristian.workflow.testhost.dto.sandbox;
import java.time.Instant;
import java.util.UUID;
public record SandboxServiceEffect(long id,long requestId,long instanceId,UUID commandId,String nodeKey,
        long quantity,String actorRef,Instant occurredAt) {}
