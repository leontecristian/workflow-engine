package io.github.leontecristian.workflow.core.repository.runtime;
/** Joined task metadata for administration and personal filters. */
public interface RuntimeTaskAdministrationRow extends RuntimeTaskRow {
 String getNodeKey(); String getTaskTypeCode(); String getTaskTypeName(); Long getDefinitionVersionId();
}
