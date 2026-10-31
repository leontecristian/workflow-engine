package io.github.leontecristian.workflow.core.dto.runtime;

/** Host-authorized monitoring page. Null status means all established states. */
public record WorkflowInstanceQuery(InstanceStatus status, long afterId, int limit) {
    public WorkflowInstanceQuery() {
        this(InstanceStatus.ACTIVE, 0, 50);
    }
}
