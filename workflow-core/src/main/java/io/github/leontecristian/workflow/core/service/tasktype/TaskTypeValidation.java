package io.github.leontecristian.workflow.core.service.tasktype;

import io.github.leontecristian.workflow.core.exception.tasktype.TaskTypeCatalogException;
import java.util.regex.Pattern;

final class TaskTypeValidation {

    private static final Pattern CODE = Pattern.compile("[A-Za-z][A-Za-z0-9._-]{0,99}");

    private TaskTypeValidation() {
    }

    static void require(boolean valid, String field) {
        if (!valid) {
            throw new TaskTypeCatalogException(TaskTypeCatalogException.Category.INVALID_INPUT, field);
        }
    }

    static void code(String code) {
        require(code != null && CODE.matcher(code).matches(), "code");
    }

    static void metadata(String name, String description) {
        require(name != null && !name.isBlank() && name.length() <= 200, "name");
        require(description == null || description.length() <= 2000, "description");
    }

    static void id(long id) {
        require(id > 0, "id");
    }
}
