package io.github.leontecristian.workflow.core.service.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionValidationReport;
import io.github.leontecristian.workflow.core.dto.definition.DiagnosticStage;
import io.github.leontecristian.workflow.core.dto.definition.TaskTypeCapability;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.tasktype.TaskTypeView;
import io.github.leontecristian.workflow.core.exception.definition.WorkflowDefinitionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.service.runtime.RuntimeHostSupport;
import io.github.leontecristian.workflow.core.service.runtime.ServiceActionRegistry;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.function.SingletonSupplier;

/** Internal publication-subset checker. No represented host function is ever executed. */
@Component
public class DefinitionValidationService {
    static final String IDENTIFIER = "wf-previous-decisions-validator-v1";
    private final TaskTypeCatalogService catalog;
    private final SingletonSupplier<Map<String, TaskTypeCapability>> taskTypes;
    private final RuntimeHostSupport runtimeHost;
    private final ObjectMapper json = new ObjectMapper();
    private final MinimalDefinitionLoader loader;
    private final ServiceActionRegistry actions;

    public DefinitionValidationService(TaskTypeCatalogService catalog, ObjectProvider<TaskTypeCapability> taskTypes,
                                       MinimalDefinitionLoader loader, ServiceActionRegistry actions,
                                       RuntimeHostSupport runtimeHost) {
        this.runtimeHost = runtimeHost;
        this.actions = actions;
        this.catalog = catalog;
        this.loader = loader;
        this.taskTypes = SingletonSupplier.of(() -> indexTaskTypes(taskTypes));
    }

    record Checked(DefinitionValidationReport report, byte[] contextHash, String detailsJson) { }

    Checked check(DefinitionDocumentCodec.Document document, boolean publication) {
        DefinitionDiagnostics diagnostics = new DefinitionDiagnostics();
        MinimalWorkflowDefinition model = loader.model(document, diagnostics);
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
        if (model != null && !model.humans().isEmpty()) {
            var capabilities = taskTypes.obtain();
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
                Set<AssignmentTargetKind> assignmentKinds;
                try {
                    assignmentKinds = runtimeHost.assignmentKinds(resolver, human.completionPolicy());
                } catch (WorkflowExecutionException failure) {
                    throw new WorkflowDefinitionException(WorkflowDefinitionException.Category.HOST_CONFIGURATION, failure);
                }
                TaskTypeCapability presentation = capabilities.get(human.taskType());
                if (assignmentKinds != null) {
                    var descriptor = humanContext.putObject("assignmentCapability"); descriptor.put("resolver", resolver);
                    descriptor.put("policy", human.completionPolicy().name());
                    var kinds = descriptor.putArray("targetKinds"); assignmentKinds.stream().map(Enum::name).sorted().forEach(kinds::add);
                }
                if (presentation != null) {
                    ObjectNode descriptor = humanContext.putObject("taskCapability"); descriptor.put("taskType", presentation.taskType());
                    var outcomes = descriptor.putArray("supportedOutcomes"); presentation.supportedOutcomes().stream().sorted().forEach(outcomes::add);
                }
                if (assignmentKinds == null || !supportsOutcomes(presentation, human))
                    diagnostics.error("HOST_CAPABILITY_UNAVAILABLE", DiagnosticStage.HOST, "Required deployed host capability is unavailable.", pointer);
            }
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

    void requireHumanCapabilities(MinimalWorkflowDefinition model, boolean starting) {
        if (model.humans().isEmpty()) return;
        var capabilities = taskTypes.obtain();
        for (var human : model.humans().values()) {
            if (!supportsOutcomes(capabilities.get(human.taskType()), human))
                throw new WorkflowDefinitionException(WorkflowDefinitionException.Category.HOST_CONFIGURATION, List.of());
            if (starting) {
                try {
                    runtimeHost.requireAssignmentResolver(human.assignment().resolver(), human.completionPolicy());
                } catch (WorkflowExecutionException failure) {
                    throw new WorkflowDefinitionException(WorkflowDefinitionException.Category.HOST_CONFIGURATION, failure);
                }
            }
        }
    }

    private static boolean supportsOutcomes(TaskTypeCapability capability, MinimalWorkflowDefinition.Human human) {
        return capability != null
                && human.outcomes().stream().allMatch(outcome -> capability.supportedOutcomes().contains(outcome.key()));
    }

    private static Map<String, TaskTypeCapability> indexTaskTypes(ObjectProvider<TaskTypeCapability> providers) {
        try {
            Map<String, TaskTypeCapability> registered = new HashMap<>();
            for (TaskTypeCapability capability : providers.stream().toList()) {
                if (!DefinitionInputs.key(capability.taskType()) || capability.supportedOutcomes() == null
                        || capability.supportedOutcomes().stream().anyMatch(key -> !DefinitionInputs.key(key))
                        || registered.putIfAbsent(capability.taskType(), capability) != null)
                    throw new IllegalArgumentException("Invalid or duplicate task capability");
            }
            return Map.copyOf(registered);
        } catch (RuntimeException failure) {
            throw new WorkflowDefinitionException(WorkflowDefinitionException.Category.HOST_CONFIGURATION, failure);
        }
    }
}
