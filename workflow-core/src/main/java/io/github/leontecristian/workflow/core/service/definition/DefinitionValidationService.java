package io.github.leontecristian.workflow.core.service.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionCapabilities;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionValidationReport;
import io.github.leontecristian.workflow.core.dto.definition.DiagnosticStage;
import io.github.leontecristian.workflow.core.dto.definition.SingleAssignmentCapability;
import io.github.leontecristian.workflow.core.dto.definition.AllAssignmentCapability;
import io.github.leontecristian.workflow.core.dto.runtime.CompletionPolicy;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.definition.TaskTypeCapability;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.tasktype.TaskTypeView;
import io.github.leontecristian.workflow.core.exception.definition.WorkflowDefinitionException;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Internal publication-subset checker. No represented host function is ever executed. */
@Component
public class DefinitionValidationService {
    static final String IDENTIFIER = "wf-all-task-validator-v1";
    private final TaskTypeCatalogService catalog;
    private final DefinitionCapabilityProvider provider;
    private final io.github.leontecristian.workflow.core.service.runtime.RuntimeHostSupport runtimeHost;
    private final ObjectMapper json = new ObjectMapper();
    private final MinimalDefinitionLoader loader;
    private final io.github.leontecristian.workflow.core.service.runtime.ServiceActionRegistry actions;

    public DefinitionValidationService(TaskTypeCatalogService catalog, ObjectProvider<DefinitionCapabilityProvider> providers,
                                       MinimalDefinitionLoader loader, io.github.leontecristian.workflow.core.service.runtime.ServiceActionRegistry actions,
                                       io.github.leontecristian.workflow.core.service.runtime.RuntimeHostSupport runtimeHost) {
        this.runtimeHost=runtimeHost;
        this.actions=actions;
        this.catalog = catalog;
        this.loader = loader;
        List<DefinitionCapabilityProvider> registered = providers.stream().toList();
        if (registered.size() > 1) throw new WorkflowDefinitionException(WorkflowDefinitionException.Category.HOST_CONFIGURATION, List.of());
        this.provider = registered.isEmpty() ? null : registered.get(0);
    }

    record Checked(DefinitionValidationReport report, byte[] contextHash, String detailsJson) { }

    Checked check(DefinitionDocumentCodec.Document document, boolean publication) {
        DefinitionDiagnostics diagnostics = new DefinitionDiagnostics();
        MinimalWorkflowDefinition model = loader.model(document, diagnostics);
        DefinitionCapabilities capabilities = snapshot();
        ObjectNode provenance = json.createObjectNode();
        boolean extended=MinimalDefinitionLoader.extended(document.tree());
        boolean routing=MinimalDefinitionLoader.routing(document.tree());
        provenance.put("validator", IDENTIFIER);
        provenance.put("structuralSchemaHash", loader.schemaDigest(document.tree()));
        if(routing) {
            provenance.put("expressionProfile",io.github.leontecristian.workflow.core.service.expression.RestrictedExpressionService.PROFILE);
            provenance.put("springExpressionVersion",io.github.leontecristian.workflow.core.service.expression.RestrictedExpressionService.SPRING_VERSION);
        }
        if (model!=null && extended) {
            ServiceBindingValidation.check(model,actions,diagnostics);
            var refs=provenance.putArray("actions");
            model.services().values().stream().map(n -> n.action()).distinct().sorted().forEach(id -> {
                var descriptor=actions.descriptor(id); if (descriptor!=null) refs.add(json.valueToTree(descriptor));
            });
        }
        provenance.put("schemaVersion", 1); provenance.put("canonicalization", DefinitionContentCanonicalizer.IDENTIFIER);
        var humanContexts = provenance.putArray("humans");
        if (model != null) {
            for (var human : model.humans().values().stream().sorted(java.util.Comparator.comparing(MinimalWorkflowDefinition.Human::key)).toList()) {
                var humanContext = humanContexts.addObject();
                humanContext.put("nodeKey", human.key());
                String pointer = null;
                for (int index = 0; index < document.tree().path("nodes").size(); index++)
                    if (human.key().equals(document.tree().path("nodes").get(index).path("key").asText())) pointer = "/nodes/" + index;
                TaskTypeView task = (publication ? catalog.findByCodeForPublication(human.taskType()) : catalog.findByCode(human.taskType())).orElse(null);
                if (task == null || !task.selectable()) diagnostics.error("TASK_TYPE_UNAVAILABLE", DiagnosticStage.HOST,
                        "Referenced task type must exist and be selectable.", pointer + "/taskType");
                if (task != null) {
                    ObjectNode descriptor = humanContext.putObject("task"); descriptor.put("code", task.code());
                    descriptor.put("selectable", task.selectable()); descriptor.put("lockVersion", task.lockVersion());
                }
                String resolver = human.assignment().resolver();
                Set<AssignmentTargetKind> assignmentKinds = null;
                if (capabilities != null) {
                    assignmentKinds = human.completionPolicy() == CompletionPolicy.ALL
                            ? capabilities.allAssignments().stream().filter(c -> c.resolver().equals(resolver))
                                    .map(AllAssignmentCapability::targetKinds).findFirst().orElse(null)
                            : capabilities.assignments().stream().filter(c -> c.resolver().equals(resolver))
                                    .map(SingleAssignmentCapability::targetKinds).findFirst().orElse(null);
                }
                TaskTypeCapability presentation = capabilities == null ? null : capabilities.taskTypes().stream()
                        .filter(c -> c.taskType().equals(human.taskType())).findFirst().orElse(null);
                if (assignmentKinds != null) {
                    if (human.completionPolicy() == CompletionPolicy.ALL) runtimeHost.requireAllResolver(resolver, assignmentKinds);
                    else runtimeHost.requireResolver(resolver, assignmentKinds);
                    var descriptor = humanContext.putObject("assignmentCapability"); descriptor.put("resolver", resolver);
                    descriptor.put("policy", human.completionPolicy().name());
                    var kinds = descriptor.putArray("targetKinds"); assignmentKinds.stream().map(Enum::name).sorted().forEach(kinds::add);
                }
                if (presentation != null) {
                    ObjectNode descriptor = humanContext.putObject("taskCapability"); descriptor.put("taskType", presentation.taskType());
                    var outcomes = descriptor.putArray("supportedOutcomes"); presentation.supportedOutcomes().stream().sorted().forEach(outcomes::add);
                }
                if (assignmentKinds == null || presentation == null || !human.outcomes().stream().allMatch(outcome -> presentation.supportedOutcomes().contains(outcome.key())))
                    diagnostics.error("HOST_CAPABILITY_UNAVAILABLE", DiagnosticStage.HOST, "Required deployed host capability is unavailable.", pointer);
            }
        } else if (capabilities == null) {
            diagnostics.error("HOST_CAPABILITY_UNAVAILABLE", DiagnosticStage.HOST, "Deployed host capability provider is unavailable.", null);
        }
        String context = DefinitionContentCanonicalizer.canonical(provenance);
        byte[] contextHash = DefinitionContentCanonicalizer.hash(context);
        DefinitionValidationReport report = diagnostics.report();
        ObjectNode evidence = json.createObjectNode(); evidence.put("reportSchemaVersion", 1);
        evidence.set("provenance", provenance); evidence.put("contextHash", DefinitionContentCanonicalizer.hex(contextHash));
        evidence.set("report", json.valueToTree(report));
        String details = DefinitionContentCanonicalizer.canonical(evidence);
        if (details.length() * 2L > 256 * 1024) throw DefinitionInputs.error("LIMIT_EXCEEDED", "Required validation provenance exceeds its storage budget.");
        return new Checked(report, contextHash, details);
    }

    DefinitionCapabilities snapshot() {
        if (provider == null) return null;
        try {
            DefinitionCapabilities snapshot = provider.snapshot();
            if (snapshot == null) throw new IllegalArgumentException("Missing deployment snapshot");
            Set<String> ids = new HashSet<>();
            for (SingleAssignmentCapability c : snapshot.assignments()) {
                if (c == null || !DefinitionInputs.text(c.resolver(), 100, 400, true) || !ids.add(c.resolver()))
                    throw new IllegalArgumentException("Invalid resolver descriptor");
            }
            ids.clear();
            for (AllAssignmentCapability c : snapshot.allAssignments()) {
                if (c == null || !DefinitionInputs.text(c.resolver(), 100, 400, true) || !ids.add(c.resolver()))
                    throw new IllegalArgumentException("Invalid ALL resolver descriptor");
            }
            ids.clear();
            for (TaskTypeCapability c : snapshot.taskTypes()) {
                if (c == null || !DefinitionInputs.key(c.taskType()) || !ids.add(c.taskType())
                        || c.supportedOutcomes().stream().anyMatch(k -> !DefinitionInputs.key(k)))
                    throw new IllegalArgumentException("Invalid task descriptor");
            }
            return snapshot;
        } catch (RuntimeException failure) {
            throw new WorkflowDefinitionException(WorkflowDefinitionException.Category.HOST_CONFIGURATION, failure);
        }
    }

}
