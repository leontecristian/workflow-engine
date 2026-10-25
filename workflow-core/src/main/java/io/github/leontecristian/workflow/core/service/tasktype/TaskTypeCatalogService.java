package io.github.leontecristian.workflow.core.service.tasktype;

import io.github.leontecristian.workflow.core.dto.tasktype.CreateTaskType;
import io.github.leontecristian.workflow.core.exception.tasktype.TaskTypeCatalogException;
import io.github.leontecristian.workflow.core.dto.tasktype.TaskTypeChange;
import io.github.leontecristian.workflow.core.dto.tasktype.TaskTypeFilter;
import io.github.leontecristian.workflow.core.dto.tasktype.TaskTypePage;
import io.github.leontecristian.workflow.core.dto.tasktype.TaskTypeQuery;
import io.github.leontecristian.workflow.core.dto.tasktype.TaskTypeView;
import io.github.leontecristian.workflow.core.dto.tasktype.UpdateTaskType;
import io.github.leontecristian.workflow.core.entity.tasktype.TaskTypeEntity;
import io.github.leontecristian.workflow.core.repository.tasktype.TaskTypeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trusted in-process catalog boundary. The host authenticates and authorizes before any call.
 * Use the managed Spring bean: writes require an existing host transaction, reads join it or
 * open a read-only transaction. Missing transaction/infrastructure and commit failures remain
 * host integration failures.
 *
 * <p>The host persists complete before/after management audit, with trusted actor/time, in the
 * same transaction for changed=true, then commits before reporting external success. A create
 * has no before snapshot. Audit failure must roll back the catalog write. Returned snapshots
 * are provisional until commit; do not continue a failed transaction or replace audit with logs.
 * No-op attempts may be audited under host policy but are not catalog changes.
 *
 * <p>Codes are permanent and case-sensitive. Nonselectable entries remain addressable.
 * This API supplies neither deletion/renaming nor host authorization/audit implementations.
 */
@Service
@Transactional(readOnly = true)
public class TaskTypeCatalogService {

    private final TaskTypeRepository repository;
    private final EntityManager entityManager;

    public TaskTypeCatalogService(TaskTypeRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    /** Validates, inserts and flushes; duplicate codes conflict rather than upsert. */
    @Transactional(propagation = Propagation.MANDATORY)
    public TaskTypeChange create(CreateTaskType command) {
        TaskTypeValidation.require(command != null, "command");
        TaskTypeValidation.code(command.code());
        TaskTypeValidation.metadata(command.name(), command.description());
        try {
            TaskTypeEntity entity = new TaskTypeEntity(
                    command.code(), command.name(), command.description(), command.selectable());
            // IDENTITY may insert during persist, so both persist and flush share the translation boundary.
            entityManager.persist(entity);
            entityManager.flush();
            return new TaskTypeChange(Optional.empty(), entity.snapshot(), true);
        } catch (PersistenceException | DataAccessException failure) {
            throw TaskTypePersistenceErrors.translate(failure, true);
        }
    }

    /** Includes nonselectable entries; an unknown valid ID returns empty. */
    public Optional<TaskTypeView> findById(long id) {
        TaskTypeValidation.id(id);
        try {
            return repository.findById(id).map(TaskTypeEntity::snapshot);
        } catch (PersistenceException | DataAccessException failure) {
            throw TaskTypePersistenceErrors.translate(failure, false);
        }
    }

    /** Exact, unnormalized code lookup, including nonselectable entries. */
    public Optional<TaskTypeView> findByCode(String code) {
        TaskTypeValidation.code(code);
        try {
            return repository.findByCode(code).map(TaskTypeEntity::snapshot);
        } catch (PersistenceException | DataAccessException failure) {
            throw TaskTypePersistenceErrors.translate(failure, false);
        }
    }

    /** Bounded ascending-ID page; continue with the same filter and returned nextAfterId. */
    public TaskTypePage list(TaskTypeQuery query) {
        TaskTypeValidation.require(query != null, "query");
        TaskTypeValidation.require(query.filter() != null, "filter");
        TaskTypeValidation.require(query.afterId() >= 0, "afterId");
        TaskTypeValidation.require(query.limit() >= 1 && query.limit() <= 100, "limit");
        Boolean selectable = null;
        if (query.filter() != TaskTypeFilter.ALL) {
            selectable = query.filter() == TaskTypeFilter.SELECTABLE;
        }
        try {
            List<TaskTypeEntity> matches = repository.findPage(
                    query.afterId(), selectable, PageRequest.of(0, query.limit() + 1));
            List<TaskTypeView> items = matches.stream().limit(query.limit())
                    .map(TaskTypeEntity::snapshot).toList();
            Long nextAfterId = matches.size() > query.limit() ? items.get(items.size() - 1).id() : null;
            return new TaskTypePage(items, nextAfterId);
        } catch (PersistenceException | DataAccessException failure) {
            throw TaskTypePersistenceErrors.translate(failure, false);
        }
    }

    /**
     * Checks expectedVersion even for an exact no-op. Real changes flush a versioned update
     * and return the actual managed version; no-op snapshots are equal without reserving state.
     * Invalid input, missing rows, stale versions and storage failures are categorized errors.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public TaskTypeChange update(UpdateTaskType command) {
        TaskTypeValidation.require(command != null, "command");
        TaskTypeValidation.id(command.id());
        TaskTypeValidation.require(command.expectedVersion() >= 0, "expectedVersion");
        TaskTypeValidation.metadata(command.name(), command.description());
        try {
            TaskTypeEntity entity = repository.findById(command.id()).orElseThrow(
                    () -> new TaskTypeCatalogException(TaskTypeCatalogException.Category.NOT_FOUND, "id"));
            TaskTypeView before = entity.snapshot();
            if (before.lockVersion() != command.expectedVersion()) {
                throw new TaskTypeCatalogException(TaskTypeCatalogException.Category.STALE_VERSION, "expectedVersion");
            }
            if (before.name().equals(command.name())
                    && Objects.equals(before.description(), command.description())
                    && before.selectable() == command.selectable()) {
                return new TaskTypeChange(Optional.of(before), before, false);
            }
            entity.replaceMetadata(command.name(), command.description(), command.selectable());
            entityManager.flush();
            return new TaskTypeChange(Optional.of(before), entity.snapshot(), true);
        } catch (PersistenceException | DataAccessException failure) {
            throw TaskTypePersistenceErrors.translate(failure, false);
        }
    }
}
