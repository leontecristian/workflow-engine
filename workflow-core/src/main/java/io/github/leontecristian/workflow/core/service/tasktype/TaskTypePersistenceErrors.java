package io.github.leontecristian.workflow.core.service.tasktype;

import io.github.leontecristian.workflow.core.exception.tasktype.TaskTypeCatalogException;
import java.sql.SQLException;
import org.hibernate.StaleStateException;
import org.hibernate.exception.ConstraintViolationException;
import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.OptimisticLockingFailureException;

final class TaskTypePersistenceErrors {

    private static final String CODE_CONSTRAINT = "uq_workflow_task_type_code";

    private TaskTypePersistenceErrors() {
    }

    static TaskTypeCatalogException translate(RuntimeException failure, boolean inserting) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof OptimisticLockException
                    || cause instanceof OptimisticLockingFailureException
                    || cause instanceof StaleStateException) {
                return new TaskTypeCatalogException(
                        TaskTypeCatalogException.Category.STALE_VERSION, null, failure);
            }
            if (inserting && cause instanceof ConstraintViolationException constraint
                    && CODE_CONSTRAINT.equalsIgnoreCase(constraint.getConstraintName())) {
                return duplicate(failure);
            }
            if (inserting && cause instanceof SQLException sql) {
                for (SQLException detail = sql; detail != null; detail = detail.getNextException()) {
                    // SQL Server's 2627 names the violated UQ; other integrity failures are storage errors.
                    if (detail.getErrorCode() == 2627 && detail.getMessage() != null
                            && detail.getMessage().contains("'" + CODE_CONSTRAINT + "'")) {
                        return duplicate(failure);
                    }
                }
            }
        }
        return new TaskTypeCatalogException(
                TaskTypeCatalogException.Category.STORAGE_FAILURE, null, failure);
    }

    private static TaskTypeCatalogException duplicate(RuntimeException failure) {
        return new TaskTypeCatalogException(
                TaskTypeCatalogException.Category.DUPLICATE_CODE, "code", failure);
    }
}
