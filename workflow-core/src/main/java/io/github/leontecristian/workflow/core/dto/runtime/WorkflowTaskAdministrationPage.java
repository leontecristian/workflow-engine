package io.github.leontecristian.workflow.core.dto.runtime;
/** Empty filtered pages may retain continuation. */
public record WorkflowTaskAdministrationPage(java.util.List<WorkflowTaskAdministrationItem> items,Long nextAfterId) {
 public WorkflowTaskAdministrationPage { items=java.util.List.copyOf(items); }
}
