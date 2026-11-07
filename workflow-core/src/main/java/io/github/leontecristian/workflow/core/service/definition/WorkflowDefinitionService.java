package io.github.leontecristian.workflow.core.service.definition;

import io.github.leontecristian.workflow.core.dto.definition.ImportWorkflowDraft;
import io.github.leontecristian.workflow.core.dto.definition.ReplaceWorkflowDraft;
import io.github.leontecristian.workflow.core.dto.definition.UpdateWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.CheckWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.RetireWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionManagementContext;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionView;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionVersionView;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionVersionSummary;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionChange;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionMetadataChange;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionCheckResult;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionVersionQuery;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionVersionPage;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionStatus;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionQuery;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionOrder;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionPage;
import io.github.leontecristian.workflow.core.entity.definition.WorkflowDefinitionEntity;
import io.github.leontecristian.workflow.core.entity.definition.WorkflowDefinitionVersionEntity;
import io.github.leontecristian.workflow.core.repository.definition.WorkflowDefinitionRepository;
import io.github.leontecristian.workflow.core.repository.definition.WorkflowDefinitionVersionRepository;
import io.github.leontecristian.workflow.core.exception.definition.WorkflowDefinitionException;
import io.github.leontecristian.workflow.core.exception.tasktype.TaskTypeCatalogException;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.OptimisticLockException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trusted definition-administration boundary; call the managed Spring bean.
 * Hosts authenticate/authorize before every call and construct the management context from trusted identity/time.
 * All mutations require an existing host transaction. For changed results, persist durable management audit
 * with operation, context and complete before/after summaries in that transaction, then commit before reporting
 * success. Audit/commit failure must roll back the change; every returned result is provisional until commit.
 * Invalid authoring is returned normally by validate/publish and may be committed with its FAILED evidence.
 * Technical failures abort the transaction: never catch one and continue it. No-op observations reserve no state.
 * No runtime workflow, HTTP, audit-storage or authorization implementation is supplied here.
 */
@Service
@Transactional(readOnly = true)
public class WorkflowDefinitionService {
    private final WorkflowDefinitionRepository definitions;
    private final WorkflowDefinitionVersionRepository versions;
    private final EntityManager entityManager;
    private final DefinitionDocumentCodec codec;
    private final DefinitionValidationService validator;
    private final TaskTypeCatalogService catalog;

    public WorkflowDefinitionService(WorkflowDefinitionRepository definitions, WorkflowDefinitionVersionRepository versions,
                                     EntityManager entityManager, DefinitionDocumentCodec codec,
                                     DefinitionValidationService validator, TaskTypeCatalogService catalog) {
        this.definitions = definitions; this.versions = versions; this.entityManager = entityManager;
        this.codec = codec; this.validator = validator; this.catalog = catalog;
    }

    /** Import is create-or-content-reuse, never upsert, renumbering or receipt replay. */
    @Transactional(propagation = Propagation.MANDATORY)
    public DefinitionChange importDraft(ImportWorkflowDraft command, DefinitionManagementContext context) {
        DefinitionInputs.require(command != null);
        LocalDateTime time = DefinitionInputs.context(context);
        DefinitionDocumentCodec.Document document = codec.ingest(command.documentJson());
        return storage(() -> {
            WorkflowDefinitionEntity family = definitions.findByWorkflowKey(document.key()).orElse(null);
            if (family != null) {
                WorkflowDefinitionVersionEntity existing = versions.findByDefinitionIdAndVersion(family.getId(), document.version()).orElse(null);
                if (existing != null) {
                    if (!equivalent(existing, document)) throw failure(WorkflowDefinitionException.Category.CONFLICT);
                    DefinitionVersionSummary summary = DefinitionViews.summary(document.key(), existing);
                    return new DefinitionChange(Optional.of(summary), summary, false, "IMPORT", context);
                }
            }
            authoringSelections(document);
            if (family == null) {
                family = new WorkflowDefinitionEntity(document.key(), document.name(), document.description(), time, context.actorRef());
                entityManager.persist(family);
            }
            WorkflowDefinitionVersionEntity version = new WorkflowDefinitionVersionEntity(family.getId(), document.version(),
                    document.canonical(), document.hash(), DefinitionContentCanonicalizer.IDENTIFIER, time, context.actorRef());
            entityManager.persist(version);
            entityManager.flush();
            return new DefinitionChange(Optional.empty(), DefinitionViews.summary(document.key(), version), true, "IMPORT", context);
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DefinitionChange replaceDraft(ReplaceWorkflowDraft command, DefinitionManagementContext context) {
        DefinitionInputs.require(command != null);
        LocalDateTime time = DefinitionInputs.context(context);
        expected(command.versionId(), command.expectedLockVersion());
        DefinitionDocumentCodec.Document document = codec.ingest(command.documentJson());
        return storage(() -> {
            WorkflowDefinitionVersionEntity version = version(command.versionId());
            checkVersion(version, command.expectedLockVersion()); draft(version);
            String key = family(version.getDefinitionId()).getWorkflowKey();
            DefinitionInputs.require(key.equals(document.key()) && version.getVersion() == document.version()
                    && version.getSchemaVersion() == 1);
            DefinitionVersionSummary before = DefinitionViews.summary(key, version);
            if (equivalent(version, document)) return change(before, before, "REPLACE_DRAFT", context);
            authoringSelections(document);
            version.replaceDraft(document.canonical(), document.hash(), DefinitionContentCanonicalizer.IDENTIFIER, time, context.actorRef());
            entityManager.flush();
            return change(before, DefinitionViews.summary(key, version), "REPLACE_DRAFT", context);
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DefinitionMetadataChange updateMetadata(UpdateWorkflowDefinition command, DefinitionManagementContext context) {
        DefinitionInputs.require(command != null);
        DefinitionInputs.context(context);
        expected(command.definitionId(), command.expectedLockVersion());
        DefinitionInputs.metadata(command.name(), command.description());
        return storage(() -> {
            WorkflowDefinitionEntity family = family(command.definitionId());
            if (family.getLockVersion() != command.expectedLockVersion()) throw failure(WorkflowDefinitionException.Category.CONFLICT);
            DefinitionView before = DefinitionViews.family(family);
            if (!family.getCatalogName().equals(command.name()) || !Objects.equals(family.getCatalogDescription(), command.description())) {
                family.replaceMetadata(command.name(), command.description());
                entityManager.flush();
            }
            DefinitionView after = DefinitionViews.family(family);
            return new DefinitionMetadataChange(Optional.of(before), after, !before.equals(after), "UPDATE_METADATA", context);
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DefinitionCheckResult validateDraft(CheckWorkflowDefinition command, DefinitionManagementContext context) {
        return check(command, context, false);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DefinitionCheckResult publish(CheckWorkflowDefinition command, DefinitionManagementContext context) {
        return check(command, context, true);
    }

    private DefinitionCheckResult check(CheckWorkflowDefinition command, DefinitionManagementContext context, boolean publish) {
        DefinitionInputs.require(command != null);
        LocalDateTime time = DefinitionInputs.context(context);
        expected(command.versionId(), command.expectedLockVersion());
        return storage(() -> {
            WorkflowDefinitionVersionEntity version = version(command.versionId());
            checkVersion(version, command.expectedLockVersion()); draft(version); supported(version);
            String key = family(version.getDefinitionId()).getWorkflowKey();
            DefinitionVersionSummary before = DefinitionViews.summary(key, version);
            DefinitionDocumentCodec.Document document = codec.ingest(version.getDefinitionJson());
            if (!key.equals(document.key()) || version.getVersion() != document.version() || !equivalent(version, document))
                throw failure(WorkflowDefinitionException.Category.HOST_CONFIGURATION);
            DefinitionValidationService.Checked checked = validator.check(document, publish);
            version.recordValidation(checked.report().valid(), checked.contextHash(), checked.detailsJson(), time, context.actorRef());
            boolean published = publish && checked.report().valid();
            if (published) version.publish(time, context.actorRef());
            entityManager.flush();
            return new DefinitionCheckResult(change(before, DefinitionViews.summary(key, version),
                    publish ? "PUBLISH" : "VALIDATE", context), checked.report(), published);
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DefinitionChange retire(RetireWorkflowDefinition command, DefinitionManagementContext context) {
        DefinitionInputs.require(command != null);
        LocalDateTime time = DefinitionInputs.context(context);
        expected(command.versionId(), command.expectedLockVersion());
        return storage(() -> {
            WorkflowDefinitionVersionEntity version = version(command.versionId());
            checkVersion(version, command.expectedLockVersion());
            String key = family(version.getDefinitionId()).getWorkflowKey();
            DefinitionVersionSummary before = DefinitionViews.summary(key, version);
            if (version.getStatus() == DefinitionStatus.DRAFT) throw failure(WorkflowDefinitionException.Category.INVALID_STATE);
            if (version.getStatus() == DefinitionStatus.PUBLISHED) {
                version.retire(time, context.actorRef()); entityManager.flush();
            }
            return change(before, DefinitionViews.summary(key, version), "RETIRE", context);
        });
    }

    public Optional<DefinitionView> findDefinition(long definitionId) {
        DefinitionInputs.require(definitionId > 0);
        return storage(() -> definitions.findById(definitionId).map(DefinitionViews::family));
    }

    /** Bounded family metadata only; includes families with versions in every lifecycle. */
    public DefinitionPage listDefinitions(DefinitionQuery query) {
        DefinitionInputs.require(query != null && query.order() != null && query.afterId() >= 0 && query.limit() >= 1 && query.limit() <= 100);
        return storage(() -> {
            var page = PageRequest.of(0, query.limit() + 1);
            var matches = query.order() == DefinitionOrder.DESC
                    ? definitions.findNewestPage(query.afterId(), page)
                    : definitions.findPage(query.afterId(), page);
            var items = matches.stream().limit(query.limit()).map(DefinitionViews::family).toList();
            Long next = matches.size() > query.limit() ? items.get(items.size() - 1).id() : null;
            return new DefinitionPage(items, next);
        });
    }

    public Optional<DefinitionVersionView> findVersion(long versionId) {
        DefinitionInputs.require(versionId > 0);
        return storage(() -> versions.findById(versionId).map(this::view));
    }

    public Optional<DefinitionVersionView> findVersionByKey(String key, int version) {
        DefinitionInputs.require(DefinitionInputs.key(key) && version > 0);
        return storage(() -> definitions.findByWorkflowKey(key).flatMap(
                f -> versions.findByDefinitionIdAndVersion(f.getId(), version)).map(this::view));
    }

    public String exportVersion(long versionId) {
        DefinitionInputs.require(versionId > 0);
        return storage(() -> version(versionId).getDefinitionJson());
    }

    public DefinitionVersionPage listVersions(DefinitionVersionQuery query) {
        DefinitionInputs.require(query != null && query.definitionId() > 0 && query.afterId() >= 0
                && query.limit() >= 1 && query.limit() <= 100);
        return storage(() -> {
            WorkflowDefinitionEntity family = definitions.findById(query.definitionId()).orElse(null);
            if (family == null) return new DefinitionVersionPage(List.of(), null);
            var rows = versions.findSummaryPage(query.definitionId(), query.status(), query.afterId(), PageRequest.of(0, query.limit() + 1));
            List<DefinitionVersionSummary> items = rows.stream().limit(query.limit()).map(r -> DefinitionViews.summary(family.getWorkflowKey(), r)).toList();
            Long next = rows.size() > query.limit() ? items.get(items.size() - 1).id() : null;
            return new DefinitionVersionPage(items, next);
        });
    }

    private DefinitionVersionView view(WorkflowDefinitionVersionEntity version) {
        return new DefinitionVersionView(DefinitionViews.summary(family(version.getDefinitionId()).getWorkflowKey(), version),
                version.getDefinitionJson(), version.getValidationDetailsJson());
    }

    private WorkflowDefinitionEntity family(long id) {
        return definitions.findById(id).orElseThrow(() -> failure(WorkflowDefinitionException.Category.NOT_FOUND));
    }

    private WorkflowDefinitionVersionEntity version(long id) {
        return versions.findById(id).orElseThrow(() -> failure(WorkflowDefinitionException.Category.NOT_FOUND));
    }

    private void authoringSelections(DefinitionDocumentCodec.Document document) {
        var nodes = document.tree().get("nodes");
        if (nodes == null || !nodes.isArray()) return;
        for (var node : nodes) {
            if ("HUMAN_TASK".equals(DefinitionDocumentCodec.string(node, "type"))) {
                String taskType = DefinitionDocumentCodec.string(node, "taskType");
                if (taskType != null) {
                    if (!DefinitionInputs.key(taskType) || catalog.findByCode(taskType).filter(t -> t.selectable()).isEmpty())
                        throw DefinitionInputs.error("TASK_TYPE_UNAVAILABLE", "New authoring selection requires an existing selectable task type.");
                }
            }
        }
    }

    private boolean equivalent(WorkflowDefinitionVersionEntity stored, DefinitionDocumentCodec.Document document) {
        supported(stored);
        return Arrays.equals(stored.getContentHash(), document.hash()) && stored.getDefinitionJson().equals(document.canonical());
    }

    private void supported(WorkflowDefinitionVersionEntity version) {
        if (!DefinitionContentCanonicalizer.IDENTIFIER.equals(version.getCanonicalizationVersion()))
            throw failure(WorkflowDefinitionException.Category.UNSUPPORTED_CANONICALIZATION);
    }

    private void draft(WorkflowDefinitionVersionEntity version) {
        if (version.getStatus() != DefinitionStatus.DRAFT) throw failure(WorkflowDefinitionException.Category.INVALID_STATE);
    }

    private void checkVersion(WorkflowDefinitionVersionEntity version, long expected) {
        if (version.getLockVersion() != expected) throw failure(WorkflowDefinitionException.Category.CONFLICT);
    }

    private static void expected(long id, long version) { DefinitionInputs.require(id > 0 && version >= 0); }

    private static DefinitionChange change(DefinitionVersionSummary before, DefinitionVersionSummary after,
                                           String operation, DefinitionManagementContext context) {
        return new DefinitionChange(Optional.of(before), after, !before.equals(after), operation, context);
    }

    private static WorkflowDefinitionException failure(WorkflowDefinitionException.Category category) {
        return new WorkflowDefinitionException(category, List.of());
    }

    private <T> T storage(Supplier<T> action) {
        try { return action.get(); }
        catch (PersistenceException | DataAccessException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof OptimisticLockException || cause instanceof OptimisticLockingFailureException
                        || cause instanceof org.hibernate.StaleStateException
                        || (cause instanceof org.hibernate.exception.ConstraintViolationException constraint
                            && constraint.getConstraintName() != null && DEFINITION_KEYS.contains(constraint.getConstraintName()))
                        || (cause instanceof java.sql.SQLException sql && sql.getErrorCode() == 2627
                            && sql.getMessage() != null && DEFINITION_KEYS.stream().anyMatch(k -> sql.getMessage().contains("'" + k + "'"))))
                    throw new WorkflowDefinitionException(WorkflowDefinitionException.Category.CONFLICT, failure);
            }
            throw new WorkflowDefinitionException(WorkflowDefinitionException.Category.HOST_CONFIGURATION, failure);
        } catch (TaskTypeCatalogException failure) {
            throw new WorkflowDefinitionException(WorkflowDefinitionException.Category.HOST_CONFIGURATION, failure);
        }
    }

    private static final java.util.Set<String> DEFINITION_KEYS = java.util.Set.of(
            "uq_workflow_definition_key", "uq_workflow_definition_version_version");
}
