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

/** Narrow core-to-core runtime boundary; lifecycle selection participates in the host transaction. */
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
        return loadForStart(new io.github.leontecristian.workflow.core.dto.runtime.StartWorkflow(null, versionId, null, null, null));
    }

    /** Select once under the lifecycle lock; callers validate selectors before entry. */
    @Transactional(propagation = Propagation.MANDATORY)
    public ExecutableDefinition loadForStart(io.github.leontecristian.workflow.core.dto.runtime.StartWorkflow command) {
        Long versionId = command.definitionVersionId();
        boolean exactSelection = versionId != null;
        long familyId;
        if (versionId != null) familyId = versions.findFamilyId(versionId).orElseThrow(this::notFound);
        else if (command.definitionId() != null) familyId = command.definitionId();
        else familyId = families.findByWorkflowKey(command.workflowKey()).orElseThrow(this::notFound).getId();
        var family = families.lockById(familyId).orElseThrow(this::notFound);
        entityManager.refresh(family, LockModeType.PESSIMISTIC_WRITE);
        if (versionId == null) {
            var candidates = versions.findLatestPublished(familyId, DefinitionStatus.PUBLISHED,
                    org.springframework.data.domain.PageRequest.of(0, 1));
            if (candidates.isEmpty()) throw new WorkflowDefinitionException(
                    WorkflowDefinitionException.Category.NO_PUBLISHED_VERSION, List.of());
            versionId = candidates.get(0);
        }
        WorkflowDefinitionVersionEntity version = versions.findById(versionId)
                .orElseThrow(() -> exactSelection ? notFound() : corrupt());
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

    /** Retained display metadata only; does not establish that a definition can execute today. */
    public java.util.Map<String, String> readNodeNames(long versionId) {
        var version = versions.findById(versionId).orElseThrow(this::corrupt);
        if (version.getStatus() != DefinitionStatus.PUBLISHED && version.getStatus() != DefinitionStatus.RETIRED)
            throw corrupt();
        String source = version.getDefinitionJson();
        if (source == null || source.length() > 4 * 1024 * 1024) throw corrupt();
        var nodes = codec.readObject(source).path("nodes");
        if (!nodes.isArray() || nodes.size() > 200) throw corrupt();
        var names = new java.util.HashMap<String, String>();
        for (var node : nodes) {
            var key = node.path("key");
            var name = node.get("name");
            if (!node.isObject() || !key.isTextual() || !DefinitionInputs.key(key.textValue())
                    || name != null && (!name.isTextual() || !DefinitionInputs.text(name.textValue(), 200, 400, true)))
                throw corrupt();
            if (names.putIfAbsent(key.textValue(), name == null ? key.textValue() : name.textValue()) != null)
                throw corrupt();
        }
        return java.util.Map.copyOf(names);
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
        capabilities.requireHumanCapabilities(definition.model(), starting);
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
        var model = loader.model(document, diagnostics, false);
        if (model == null || diagnostics.failed()) throw corrupt();
        return new ExecutableDefinition(version.getId(), version.getDefinitionId(), document.key(),
                document.version(), DefinitionContentCanonicalizer.hex(document.hash()), version.getStatus(), model);
    }

    private WorkflowDefinitionException notFound() {
        return new WorkflowDefinitionException(WorkflowDefinitionException.Category.NOT_FOUND, List.of());
    }

    private WorkflowDefinitionException corrupt() {
        return new WorkflowDefinitionException(WorkflowDefinitionException.Category.HOST_CONFIGURATION, List.of());
    }
}
