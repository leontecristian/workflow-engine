package io.github.leontecristian.workflow.core.entity.runtime;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import lombok.Getter;

/** Owner-aware internal persistence key; comparisons preserve case and content. */
@Embeddable
@Getter
public class WorkflowHistoryId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;


    @Column(name = "instance_id", nullable = false)
    private Long instanceId;

    @Column(name = "sequence_number", nullable = false)
    private Long sequenceNumber;

    protected WorkflowHistoryId() {
    }

    public WorkflowHistoryId(Long instanceId, Long sequenceNumber) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(sequenceNumber, "sequenceNumber");
        if (instanceId <= 0) {
            throw new IllegalArgumentException("instanceId must be positive");
        }
        if (sequenceNumber <= 0) {
            throw new IllegalArgumentException("sequenceNumber must be positive");
        }
        this.instanceId = instanceId;
        this.sequenceNumber = sequenceNumber;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WorkflowHistoryId that)) {
            return false;
        }
        return Objects.equals(instanceId, that.instanceId) && Objects.equals(sequenceNumber, that.sequenceNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instanceId, sequenceNumber);
    }
}
