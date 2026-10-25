package io.github.leontecristian.workflow.core.dto.tasktype;

import java.util.List;

/** Current-read page, not a cross-request snapshot; a null cursor means no observed next match. */
public record TaskTypePage(List<TaskTypeView> items, Long nextAfterId) {

    public TaskTypePage {
        items = List.copyOf(items);
    }
}
