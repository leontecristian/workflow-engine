package io.github.leontecristian.workflow.core.service.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionCapabilities;
import io.github.leontecristian.workflow.core.dto.definition.SingleAssignmentCapability;
import io.github.leontecristian.workflow.core.dto.definition.TaskTypeCapability;
import io.github.leontecristian.workflow.core.dto.tasktype.TaskTypeView;
import io.github.leontecristian.workflow.core.service.runtime.ServiceActionRegistry;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import static org.assertj.core.api.Assertions.assertThat;
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
        var supported=new java.util.concurrent.atomic.AtomicReference<>(Set.of(new TaskTypeCapability("SANDBOX_REVIEW",Set.of("APPROVE","REJECT"))));
        DefinitionCapabilityProvider provider=()->new DefinitionCapabilities(Set.of(new SingleAssignmentCapability("sandbox.singleReviewer")),supported.get());
        ObjectProvider<DefinitionCapabilityProvider> providers=mock(ObjectProvider.class);
        when(providers.stream()).thenAnswer(call->java.util.stream.Stream.of(provider));
        var validation=new DefinitionValidationService(catalog,providers,loader,mock(ServiceActionRegistry.class));
        var missing=validation.check(codec.ingest(root.toString()),false);
        assertThat(missing.report().diagnostics()).anyMatch(d->d.code().equals("WF_DEF_HOST_CAPABILITY_UNAVAILABLE"));
        supported.set(Set.of(new TaskTypeCapability("SANDBOX_REVIEW",Set.of("APPROVE","REJECT")),new TaskTypeCapability("LATER",Set.of("APPROVE","REJECT"))));
        var accepted=validation.check(codec.ingest(root.toString()),false);
        assertThat(accepted.report().diagnostics()).isEmpty();
        assertThat(accepted.contextHash()).isNotEqualTo(missing.contextHash());
        assertThat(accepted.detailsJson()).contains("wf-sequential-human-validator-v1","MANAGER","PARTNER");
    }
}
