package io.github.leontecristian.workflow.core.service.definition;

import io.github.leontecristian.workflow.core.dto.definition.DefinitionStatus;
import io.github.leontecristian.workflow.core.dto.definition.document.ExecutableDefinition;
import io.github.leontecristian.workflow.core.entity.definition.WorkflowDefinitionVersionEntity;
import io.github.leontecristian.workflow.core.exception.definition.WorkflowDefinitionException;
import io.github.leontecristian.workflow.core.repository.definition.WorkflowDefinitionRepository;
import io.github.leontecristian.workflow.core.repository.definition.WorkflowDefinitionVersionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Narrow core-to-core boundary. No publication checks, authoring selectability or validation writes. */
@Service
@Transactional(readOnly = true)
public class WorkflowDefinitionRuntimeReader {
    private final WorkflowDefinitionRepository families;
    private final WorkflowDefinitionVersionRepository versions;
    private final EntityManager entityManager;
    private final DefinitionDocumentCodec codec;
    private final MinimalDefinitionLoader loader;
    private final DefinitionValidationService capabilities;
    private final io.github.leontecristian.workflow.core.service.runtime.ServiceActionRegistry actions;

    public WorkflowDefinitionRuntimeReader(WorkflowDefinitionRepository families,
            WorkflowDefinitionVersionRepository versions, EntityManager entityManager,
            DefinitionDocumentCodec codec, MinimalDefinitionLoader loader, DefinitionValidationService capabilities, io.github.leontecristian.workflow.core.service.runtime.ServiceActionRegistry actions) {
        this.actions=actions;
        this.families = families; this.versions = versions; this.entityManager = entityManager;
        this.codec = codec; this.loader = loader; this.capabilities = capabilities;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ExecutableDefinition loadForStart(long versionId) {
        WorkflowDefinitionVersionEntity version = versions.findById(versionId).orElseThrow(this::corrupt);
        entityManager.refresh(version, LockModeType.PESSIMISTIC_WRITE);
        if (version.getStatus() != DefinitionStatus.PUBLISHED)
            throw new WorkflowDefinitionException(WorkflowDefinitionException.Category.INVALID_STATE, List.of());
        return inspect(version);
    }

    public ExecutableDefinition loadPinned(long versionId) {
        WorkflowDefinitionVersionEntity version = versions.findById(versionId).orElseThrow(this::corrupt);
        if (version.getStatus() != DefinitionStatus.PUBLISHED && version.getStatus() != DefinitionStatus.RETIRED) throw corrupt();
        return inspect(version);
    }

    /** Read-only identity projection also permits stored nonminimal/cancelled aggregate views. */
    public io.github.leontecristian.workflow.core.dto.definition.document.PinnedDefinitionIdentity readIdentity(long versionId) {
        var version = versions.findById(versionId).orElseThrow(this::corrupt);
        if (version.getStatus() != DefinitionStatus.PUBLISHED && version.getStatus() != DefinitionStatus.RETIRED) throw corrupt();
        var family = families.findById(version.getDefinitionId()).orElseThrow(this::corrupt);
        return new io.github.leontecristian.workflow.core.dto.definition.document.PinnedDefinitionIdentity(
                version.getId(), family.getWorkflowKey(), version.getVersion(), DefinitionContentCanonicalizer.hex(version.getContentHash()));
    }

    public void requireCapabilities(ExecutableDefinition definition, boolean starting) {
        var diagnostics=new DefinitionDiagnostics();
        ServiceBindingValidation.check(definition.model(),actions,diagnostics);
        if (diagnostics.failed()) throw corrupt();
        var snapshot = capabilities.snapshot();
        var human = definition.model().human();
        if (snapshot == null || snapshot.taskTypes().stream().noneMatch(c -> c.taskType().equals(human.taskType())
                && human.outcomes().stream().allMatch(outcome -> c.supportedOutcomes().contains(outcome.key())))
                || (starting && snapshot.assignments().stream().noneMatch(c -> c.resolver().equals(human.assignment().resolver()))))
            throw corrupt();
    }

    private ExecutableDefinition inspect(WorkflowDefinitionVersionEntity version) {
        if (!DefinitionContentCanonicalizer.IDENTIFIER.equals(version.getCanonicalizationVersion()))
            throw new WorkflowDefinitionException(WorkflowDefinitionException.Category.UNSUPPORTED_CANONICALIZATION, List.of());
        var family = families.findById(version.getDefinitionId()).orElseThrow(this::corrupt);
        var document = codec.ingest(version.getDefinitionJson());
        if (version.getSchemaVersion() != 1 || !family.getWorkflowKey().equals(document.key())
                || version.getVersion() != document.version() || !version.getDefinitionJson().equals(document.canonical())
                || !Arrays.equals(version.getContentHash(), document.hash())) throw corrupt();
        DefinitionDiagnostics diagnostics = new DefinitionDiagnostics();
        var model = loader.model(document, diagnostics);
        if (model == null || diagnostics.failed()) throw corrupt();
        return new ExecutableDefinition(version.getId(), version.getDefinitionId(), document.key(),
                document.version(), DefinitionContentCanonicalizer.hex(document.hash()), version.getStatus(), model);
    }

    private WorkflowDefinitionException corrupt() {
        return new WorkflowDefinitionException(WorkflowDefinitionException.Category.HOST_CONFIGURATION, List.of());
    }
}
