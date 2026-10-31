package io.github.leontecristian.workflow.core.dto.runtime;
import java.util.List;
public record TaskSubmissionPage(List<TaskSubmission> items, Long nextAfterTaskId) {
    public TaskSubmissionPage { items = List.copyOf(items); }
}
