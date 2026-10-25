package io.github.leontecristian.workflow.core.dto.tasktype;

/** Detached immutable snapshot; lockVersion is a technical edit token, not a business version. */
public record TaskTypeView(long id, String code, String name, String description,
                           boolean selectable, long lockVersion) {
}
