package io.github.leontecristian.workflow.core.service.runtime;
import io.github.leontecristian.workflow.core.dto.runtime.AllocatedWorkflowInstance;
/** Per-invocation host callback: use the participating transaction, never commit/reenter or emit remote effects. */
@FunctionalInterface
public interface StartLinkAction { void link(AllocatedWorkflowInstance instance); }
