package io.github.leontecristian.workflow.core.service.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.leontecristian.workflow.core.dto.definition.DefinitionOrder;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionQuery;
import io.github.leontecristian.workflow.core.entity.definition.WorkflowDefinitionEntity;
import io.github.leontecristian.workflow.core.exception.definition.WorkflowDefinitionException;
import io.github.leontecristian.workflow.core.repository.definition.WorkflowDefinitionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class DefinitionOrderingUnitTest {
    private final WorkflowDefinitionRepository repository = mock(WorkflowDefinitionRepository.class);
    private final WorkflowDefinitionService service = new WorkflowDefinitionService(repository, null, null, null, null, null);

    private WorkflowDefinitionEntity family(long id) {
        var entity = mock(WorkflowDefinitionEntity.class);
        when(entity.getId()).thenReturn(id);
        when(entity.getLockVersion()).thenReturn(0L);
        return entity;
    }

    @Test void descendingPagesKeepExactCursorAndContinueToOlderFamilies() {
        long newest = 9007199254740995L, middle = newest - 1, oldest = newest - 2;
        var page = PageRequest.of(0, 3);
        var newestFamily = family(newest);
        var middleFamily = family(middle);
        var oldestFamily = family(oldest);
        when(repository.findNewestPage(0, page)).thenReturn(List.of(newestFamily, middleFamily, oldestFamily));
        var first = service.listDefinitions(new DefinitionQuery(0, 2, DefinitionOrder.DESC));
        assertThat(first.items()).extracting(item -> item.id()).containsExactly(newest, middle);
        assertThat(first.nextAfterId()).isEqualTo(middle);
        when(repository.findNewestPage(middle, page)).thenReturn(List.of(oldestFamily));
        var next = service.listDefinitions(new DefinitionQuery(first.nextAfterId(), 2, DefinitionOrder.DESC));
        assertThat(next.items()).extracting(item -> item.id()).containsExactly(oldest);
        assertThat(next.nextAfterId()).isNull();
    }

    @Test void existingConstructorsRetainAscendingOrderAndInvalidOrderFails() {
        assertThat(new DefinitionQuery().order()).isEqualTo(DefinitionOrder.ASC);
        var families = List.of(family(1), family(2), family(3));
        when(repository.findPage(0, PageRequest.of(0, 3))).thenReturn(families);
        var first = service.listDefinitions(new DefinitionQuery(0, 2));
        assertThat(first.items()).extracting(item -> item.id()).containsExactly(1L, 2L);
        assertThat(first.nextAfterId()).isEqualTo(2L);
        verify(repository).findPage(0, PageRequest.of(0, 3));
        assertThatThrownBy(() -> service.listDefinitions(new DefinitionQuery(0, 2, null)))
                .isInstanceOf(WorkflowDefinitionException.class);
    }
}
