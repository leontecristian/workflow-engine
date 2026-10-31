package io.github.leontecristian.workflow.core.service.runtime;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowEvent;
import java.util.List;
/**
 * Required host SPI. Insert the complete ordered batch into durable storage in the existing host
 * transaction before returning. Never commit independently, reenter mutations or acknowledge an
 * in-memory/after-commit substitute. Delivery and idempotent dispatch after commit are host-owned.
 */
@FunctionalInterface
public interface WorkflowEventRecorder { void record(List<WorkflowEvent> events); }
