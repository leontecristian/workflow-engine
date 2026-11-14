package io.github.leontecristian.workflow.core.dto.runtime;
/** Metadata only, without protected review inputs. */
public record WorkflowTaskAdministrationItem(WorkflowTaskView task,String nodeKey,String nodeName,String taskTypeCode,String taskTypeName) {}
