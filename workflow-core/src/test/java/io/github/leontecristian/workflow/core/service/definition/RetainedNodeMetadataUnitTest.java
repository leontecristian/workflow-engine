package io.github.leontecristian.workflow.core.service.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.leontecristian.workflow.core.dto.definition.DefinitionStatus;
import io.github.leontecristian.workflow.core.entity.definition.WorkflowDefinitionVersionEntity;
import io.github.leontecristian.workflow.core.exception.definition.WorkflowDefinitionException;
import io.github.leontecristian.workflow.core.repository.definition.WorkflowDefinitionRepository;
import io.github.leontecristian.workflow.core.repository.definition.WorkflowDefinitionVersionRepository;
import io.github.leontecristian.workflow.core.service.runtime.ServiceActionRegistry;
import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RetainedNodeMetadataUnitTest {
    private final WorkflowDefinitionVersionRepository versions = mock(WorkflowDefinitionVersionRepository.class);
    private final WorkflowDefinitionVersionEntity version = mock(WorkflowDefinitionVersionEntity.class);
    private final MinimalDefinitionLoader loader = mock(MinimalDefinitionLoader.class);
    private final DefinitionValidationService capabilities = mock(DefinitionValidationService.class);
    private final WorkflowDefinitionRuntimeReader reader = new WorkflowDefinitionRuntimeReader(
            mock(WorkflowDefinitionRepository.class), versions, mock(EntityManager.class),
            new DefinitionDocumentCodec(), loader, capabilities, mock(ServiceActionRegistry.class));

    private void retained(String document) {
        when(versions.findById(42L)).thenReturn(Optional.of(version));
        when(version.getStatus()).thenReturn(DefinitionStatus.RETIRED);
        when(version.getCanonicalizationVersion()).thenReturn("older-retained-format");
        when(version.getDefinitionJson()).thenReturn(document);
    }

    @Test void retainedNamesDoNotRequireCurrentExecutionFormat() {
        retained("{\"nodes\":[{\"key\":\"REVIEW\",\"name\":\"Original review\"},{\"key\":\"NEXT\"}]}");
        assertEquals(Map.of("REVIEW", "Original review", "NEXT", "NEXT"), reader.readNodeNames(42));
        var failure = assertThrows(WorkflowDefinitionException.class, () -> reader.loadPinned(42));
        assertEquals(WorkflowDefinitionException.Category.UNSUPPORTED_CANONICALIZATION, failure.category());
        verifyNoInteractions(loader, capabilities);
    }

    @Test void malformedMetadataAndDraftsStillFailClosed() {
        for (String document : java.util.List.of("{}", "{\"nodes\":{}}",
                "{\"nodes\":[{\"key\":\"REVIEW\"},{\"key\":\"REVIEW\"}]}",
                "{\"nodes\":[{\"key\":\"invalid key\"}]}",
                "{\"nodes\":[{\"key\":\"REVIEW\",\"name\":42}]}")) {
            retained(document);
            assertThrows(WorkflowDefinitionException.class, () -> reader.readNodeNames(42));
        }
        retained("{\"nodes\":[{\"key\":\"REVIEW\"}]}");
        when(version.getStatus()).thenReturn(DefinitionStatus.DRAFT);
        assertThrows(WorkflowDefinitionException.class, () -> reader.readNodeNames(42));
    }
}
