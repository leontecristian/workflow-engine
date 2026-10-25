package io.github.leontecristian.workflow.core.exception.tasktype;

/**
 * Catalog business/storage failure. Only category and field are safe public context.
 * The cause is retained for internal diagnosis and must not be serialized to clients.
 * Hosts must roll back failed writes, including errors after this service returns.
 */
public class TaskTypeCatalogException extends RuntimeException {

    public enum Category {
        INVALID_INPUT,
        NOT_FOUND,
        DUPLICATE_CODE,
        STALE_VERSION,
        STORAGE_FAILURE
    }

    private final Category category;
    private final String field;

    public TaskTypeCatalogException(Category category, String field) {
        this(category, field, null);
    }

    public TaskTypeCatalogException(Category category, String field, Throwable cause) {
        super(category.name() + (field == null ? "" : ": " + field), cause);
        this.category = category;
        this.field = field;
    }

    public Category getCategory() {
        return category;
    }

    public String getField() {
        return field;
    }
}
