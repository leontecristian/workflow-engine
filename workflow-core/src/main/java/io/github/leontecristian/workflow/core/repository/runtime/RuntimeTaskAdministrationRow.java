package io.github.leontecristian.workflow.core.repository.runtime;
/** Joined administration metadata. */
public interface RuntimeTaskAdministrationRow extends RuntimeTaskRow {
 String getNodeKey(); String getTaskTypeCode(); String getTaskTypeName(); Long getDefinitionVersionId();
}
