package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import jakarta.persistence.OptimisticLockException;
import java.sql.SQLException;
import org.hibernate.StaleStateException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

final class RuntimePersistenceErrors {
    private static final String RECEIPT_KEY = "pk_workflow_command_receipt";
    private RuntimePersistenceErrors() { }
    static <T> T call(java.util.function.Supplier<T> action) {
        try { return action.get(); }
        catch (WorkflowExecutionException failure) { throw failure; }
        catch (jakarta.persistence.PersistenceException | org.springframework.dao.DataAccessException failure) { throw translate(failure); }
        catch (io.github.leontecristian.workflow.core.exception.definition.WorkflowDefinitionException failure) {
            Category category = switch (failure.category()) {
                case UNSUPPORTED_CANONICALIZATION -> Category.UNSUPPORTED_VERSION;
                case INVALID_INPUT -> failure.diagnostics().stream().anyMatch(d -> "WF_DEF_UNSUPPORTED_SCHEMA_VERSION".equals(d.code()))
                        ? Category.UNSUPPORTED_VERSION : Category.HOST_CONFIGURATION;
                case INVALID_STATE -> Category.INVALID_STATE;
                default -> Category.HOST_CONFIGURATION;
            };
            throw new WorkflowExecutionException(category, failure);
        } catch (RuntimeException failure) { throw new WorkflowExecutionException(Category.HOST_CONFIGURATION, failure); }
    }
    static WorkflowExecutionException translate(RuntimeException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof OptimisticLockException || cause instanceof StaleStateException
                    || cause instanceof OptimisticLockingFailureException) return new WorkflowExecutionException(Category.STALE_VERSION, failure);
            if (cause instanceof ConstraintViolationException constraint
                    && RECEIPT_KEY.equalsIgnoreCase(constraint.getConstraintName()))
                return new WorkflowExecutionException(Category.CONCURRENT_COMMAND, failure);
            if (cause instanceof SQLException sql) {
                for (SQLException detail = sql; detail != null; detail = detail.getNextException()) {
                    if (detail.getErrorCode() == 2627 && detail.getMessage() != null
                            && detail.getMessage().contains("'" + RECEIPT_KEY + "'"))
                        return new WorkflowExecutionException(Category.CONCURRENT_COMMAND, failure);
                }
            }
        }
        return new WorkflowExecutionException(Category.HOST_CONFIGURATION, failure);
    }
}
