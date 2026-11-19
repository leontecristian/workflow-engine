package io.github.leontecristian.workflow.core.service.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionStatus;
import io.github.leontecristian.workflow.core.dto.definition.TaskTypeCapability;
import io.github.leontecristian.workflow.core.dto.definition.document.ExecutableDefinition;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.CompletionPolicy;
import io.github.leontecristian.workflow.core.dto.tasktype.TaskTypeView;
import io.github.leontecristian.workflow.core.exception.definition.WorkflowDefinitionException;
import io.github.leontecristian.workflow.core.service.runtime.AllTaskAssignmentResolver;
import io.github.leontecristian.workflow.core.service.runtime.RuntimeHostSupport;
import io.github.leontecristian.workflow.core.service.runtime.ServiceActionRegistry;
import io.github.leontecristian.workflow.core.service.runtime.SingleTaskAssignmentResolver;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowEventBatchWriter;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowTaskEligibilityService;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SequentialDefinitionUnitTest {
    private final ObjectMapper json = new ObjectMapper();
    private final MinimalDefinitionLoader loader = new MinimalDefinitionLoader();
    private final DefinitionDocumentCodec codec = new DefinitionDocumentCodec();

    private ObjectNode fixture(String name) throws Exception {
        Path path = Path.of("docs/features/F011-sequential-human-stages/" + name + ".json");
        if (!Files.exists(path)) path = Path.of("..").resolve(path);
        return (ObjectNode) json.readTree(Files.readString(path));
    }
    private DefinitionDiagnostics check(ObjectNode root) {
        var diagnostics = new DefinitionDiagnostics();
        loader.model(codec.ingest(root.toString()), diagnostics);
        return diagnostics;
    }
    @ParameterizedTest @ValueSource(strings={"sequential-review", "optional-stage-review", "three-stage-review"})
    void allFixturesAndReorderedNodeArraysRetainExactContracts(String name) throws Exception {
        var root = fixture(name);
        var nodes = (ArrayNode) root.get("nodes");
        nodes.add(nodes.remove(0));
        var diagnostics = new DefinitionDiagnostics();
        var model = loader.model(codec.ingest(root.toString()), diagnostics);
        assertThat(diagnostics.report().diagnostics()).isEmpty();
        assertThat(model.humans()).containsKeys("MANAGER", "PARTNER");
        assertThat(model.humans().get("PARTNER").outcome("REJECT").orElseThrow().requiresComment()).isTrue();
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, () -> model.humans().clear());
    }
    @Test void laterMissingAndDuplicateRoutesPointToThatNodesOutcome() throws Exception {
        for (boolean duplicate : new boolean[]{false,true}) {
            var root = fixture("sequential-review");
            var edges = (ArrayNode) root.get("transitions");
            if (duplicate) edges.add(((ObjectNode)edges.get(3)).deepCopy().put("key", "duplicate"));
            else edges.remove(3);
            assertThat(check(root).report().diagnostics()).anySatisfy(d -> {
                assertThat(d.code()).isEqualTo("WF_DEF_INVALID_GRAPH");
                assertThat(d.pointer()).isEqualTo("/nodes/2/outcomes/0");
            });
        }
    }
    @ParameterizedTest @ValueSource(strings={"cycle","unreachable","deadEnd","mismatch","bypass","ALL","guards","result"})
    void rejectsInvalidLaterStagesAndHumanBypass(String variant) throws Exception {
        var root = fixture("sequential-review");
        var nodes = (ArrayNode) root.get("nodes"); var edges = (ArrayNode) root.get("transitions");
        var partner = (ObjectNode) nodes.get(2);
        switch (variant) {
            case "cycle" -> ((ObjectNode)edges.get(3)).put("to","MANAGER");
            case "unreachable" -> ((ObjectNode)edges.get(1)).put("to","APPROVED");
            case "deadEnd" -> { edges.remove(4); edges.remove(3); }
            case "mismatch" -> ((ObjectNode)edges.get(3)).put("outcome","UNKNOWN");
            case "bypass" -> {
                nodes.addObject().put("key","GATE").put("type","EXCLUSIVE_GATEWAY").put("name","Gate");
                ((ObjectNode)edges.get(0)).put("to","GATE");
                edges.addObject().put("key","human").put("from","GATE").put("to","MANAGER").put("condition","false");
                edges.addObject().put("key","bypass").put("from","GATE").put("to","APPROVED").put("default",true);
            }
            case "ALL" -> ((ObjectNode)partner.get("completion")).put("policy","ALL");
            case "guards" -> ((ObjectNode)partner.path("outcomes").get(0)).putArray("guards").addObject().put("guard","later");
            case "result" -> ((ObjectNode)partner.get("resultContract")).put("mode","REQUIRED");
        }
        assertThat(check(root).failed()).isTrue();
    }
    @Test void alternativeFirstStagesAndReconvergenceAreValid() throws Exception {
        var root=fixture("three-stage-review");
        var nodes=(ArrayNode)root.get("nodes"); var edges=(ArrayNode)root.get("transitions");
        nodes.addObject().put("key","GATE").put("type","EXCLUSIVE_GATEWAY").put("name","Gate");
        ((ObjectNode)edges.get(0)).put("to","GATE");
        ((ObjectNode)edges.get(1)).put("to","FINAL");
        edges.addObject().put("key","manager").put("from","GATE").put("to","MANAGER").put("condition","true");
        edges.addObject().put("key","partner").put("from","GATE").put("to","PARTNER").put("default",true);
        assertThat(check(root).report().diagnostics()).isEmpty();
    }
    @Test void capabilityContextIncludesEveryLaterNodeAndCatalogRevision() throws Exception {
        var root=fixture("sequential-review");
        ((ObjectNode)root.path("nodes").get(2)).put("taskType","LATER");
        var catalog=mock(TaskTypeCatalogService.class);
        when(catalog.findByCode("SANDBOX_REVIEW")).thenReturn(Optional.of(new TaskTypeView(1,"SANDBOX_REVIEW","Review",null,true,0)));
        when(catalog.findByCode("LATER")).thenReturn(Optional.of(new TaskTypeView(2,"LATER","Later",null,true,0)));
        var document = codec.ingest(root.toString());
        var resolver = resolver("sandbox.singleReviewer");
        var review = new TaskTypeCapability("SANDBOX_REVIEW", Set.of("APPROVE", "REJECT"));
        var later = new TaskTypeCapability("LATER", Set.of("APPROVE", "REJECT"));
        var missing = validation(catalog, resolver, review).check(document, false);
        assertThat(missing.report().diagnostics()).anyMatch(d -> d.code().equals("WF_DEF_HOST_CAPABILITY_UNAVAILABLE"));
        var accepted = validation(catalog, resolver, review, later).check(document, false);
        assertThat(accepted.report().diagnostics()).isEmpty();
        assertThat(accepted.contextHash()).isNotEqualTo(missing.contextHash());
        assertThat(accepted.detailsJson()).contains("wf-previous-decisions-validator-v1", "MANAGER", "PARTNER");
        verify(resolver, never()).resolve(any());
    }

    @Test void addingAllResolverEnablesItsPolicyWithoutChangingTheTaskCapability() throws Exception {
        var root = fixture("sequential-review");
        var catalog = catalogWithReview();
        var single = resolver("sandbox.singleReviewer");
        var capability = new TaskTypeCapability("SANDBOX_REVIEW", Set.of("COMPLETE", "APPROVE", "REJECT"));
        var singleOnly = validation(catalog, single, capability);
        assertThat(singleOnly.check(codec.ingest(root.toString()), true).report().diagnostics()).isEmpty();

        ((ObjectNode) root.path("nodes").get(1).path("completion"))
                .put("policy", "ALL").put("contributionOutcome", "APPROVE");
        var allDocument = codec.ingest(root.toString());
        assertThat(singleOnly.check(allDocument, true).report().diagnostics())
                .extracting(d -> d.code()).containsOnly("WF_DEF_HOST_CAPABILITY_UNAVAILABLE");
        var all = mock(AllTaskAssignmentResolver.class);
        when(all.identifier()).thenReturn("sandbox.singleReviewer");
        when(all.targetKinds()).thenReturn(Set.of(AssignmentTargetKind.USER));
        var combined = validation(catalog, single, all, capability).check(allDocument, true);
        assertThat(combined.report().diagnostics()).isEmpty();
        var humans = json.readTree(combined.detailsJson()).path("provenance").path("humans");
        assertThat(humans.get(0).path("assignmentCapability").path("policy").textValue()).isEqualTo("ALL");
        assertThat(humans.get(1).path("assignmentCapability").path("policy").textValue()).isEqualTo("SINGLE");
        verify(single, never()).resolve(any());
        verify(all, never()).resolve(any());
    }

    @Test void laterMissingResolverFailsFreshStartButNotTheCompletionCapabilityGate() throws Exception {
        var root = fixture("sequential-review");
        ((ObjectNode) root.path("nodes").get(2).path("assignment")).put("resolver", "laterReviewer");
        var document = codec.ingest(root.toString());
        var model = loader.model(document, new DefinitionDiagnostics());
        var definition = new ExecutableDefinition(1, 1, document.key(), 1, "hash", DefinitionStatus.PUBLISHED, model);
        var catalog = catalogWithReview();
        var initial = resolver("sandbox.singleReviewer");
        var later = resolver("laterReviewer");
        var capability = new TaskTypeCapability("SANDBOX_REVIEW", Set.of("APPROVE", "REJECT"));
        var before = validation(catalog, initial, later, capability);
        assertThat(before.check(document, true).report().diagnostics()).isEmpty();
        var reader = new WorkflowDefinitionRuntimeReader(null, null, null, codec, loader,
                validation(catalog, initial, capability), mock(ServiceActionRegistry.class));
        assertThatThrownBy(() -> reader.requireCapabilities(definition, true))
                .isInstanceOfSatisfying(WorkflowDefinitionException.class,
                        failure -> assertThat(failure.category()).isEqualTo(WorkflowDefinitionException.Category.HOST_CONFIGURATION));
        reader.requireCapabilities(definition, false);
        verify(initial, never()).resolve(any());
        verify(later, never()).resolve(any());
    }

    @Test void publicationRequiresCatalogSelectionAndTaskCapabilityIndependently() throws Exception {
        var document = codec.ingest(fixture("sequential-review").toString());
        var catalog = catalogWithReview();
        var resolver = resolver("sandbox.singleReviewer");
        var capability = new TaskTypeCapability("SANDBOX_REVIEW", Set.of("APPROVE", "REJECT"));
        assertThat(validation(catalog).check(document, true).report().diagnostics())
                .extracting(d -> d.code()).containsOnly("WF_DEF_HOST_CAPABILITY_UNAVAILABLE");
        when(catalog.findByCodeForPublication("SANDBOX_REVIEW")).thenReturn(Optional.empty());
        assertThat(validation(catalog, resolver, capability).check(document, true).report().diagnostics())
                .extracting(d -> d.code()).containsOnly("WF_DEF_TASK_TYPE_UNAVAILABLE");
        when(catalog.findByCodeForPublication("SANDBOX_REVIEW")).thenReturn(Optional.of(
                new TaskTypeView(1, "SANDBOX_REVIEW", "Review", null, false, 1)));
        assertThat(validation(catalog, resolver, capability).check(document, true).report().diagnostics())
                .extracting(d -> d.code()).containsOnly("WF_DEF_TASK_TYPE_UNAVAILABLE");
    }

    @Test void validatesTaskTypeMetadataWhenConsumed() throws Exception {
        var document = codec.ingest(fixture("sequential-review").toString());
        var catalog = catalogWithReview();
        var capability = new TaskTypeCapability("SANDBOX_REVIEW", Set.of("APPROVE", "REJECT"));
        for (var invalid : new TaskTypeCapability[] {
                new TaskTypeCapability("SANDBOX_REVIEW", Set.of("APPROVE")),
                new TaskTypeCapability("bad code", Set.of("APPROVE")),
                new TaskTypeCapability("OTHER", Set.of("bad outcome")),
                new TaskTypeCapability("OTHER", null),
                new TaskTypeCapability("OTHER", java.util.Collections.singleton(null))
        }) {
            var validation = validation(catalog, capability, invalid);
            assertThatThrownBy(() -> validation.check(document, true))
                    .isInstanceOfSatisfying(WorkflowDefinitionException.class,
                            failure -> assertThat(failure.category()).isEqualTo(WorkflowDefinitionException.Category.HOST_CONFIGURATION));
        }
    }

    private TaskTypeCatalogService catalogWithReview() {
        var catalog = mock(TaskTypeCatalogService.class);
        when(catalog.findByCodeForPublication("SANDBOX_REVIEW")).thenReturn(Optional.of(
                new TaskTypeView(1, "SANDBOX_REVIEW", "Review", null, true, 0)));
        return catalog;
    }

    private SingleTaskAssignmentResolver resolver(String identifier) {
        var resolver = mock(SingleTaskAssignmentResolver.class);
        when(resolver.identifier()).thenReturn(identifier);
        when(resolver.targetKinds()).thenReturn(Set.of(AssignmentTargetKind.USER));
        return resolver;
    }

    private DefinitionValidationService validation(TaskTypeCatalogService catalog, Object... registrations) {
        var beans = new DefaultListableBeanFactory();
        for (int index = 0; index < registrations.length; index++) {
            beans.registerSingleton("registration" + index, registrations[index]);
        }
        var host = new RuntimeHostSupport(beans.getBeanProvider(SingleTaskAssignmentResolver.class),
                beans.getBeanProvider(WorkflowEventBatchWriter.class), mock(WorkflowTaskEligibilityService.class),
                beans.getBeanProvider(AllTaskAssignmentResolver.class));
        return new DefinitionValidationService(catalog, beans.getBeanProvider(TaskTypeCapability.class), loader,
                mock(ServiceActionRegistry.class), host);
    }
}
