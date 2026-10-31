package io.github.leontecristian.workflow.core.repository.definition;

import io.github.leontecristian.workflow.core.entity.definition.WorkflowDefinitionVersionEntity;
import java.util.Optional;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal reads only; results grant no authorization and must not escape into host responses.
 * Callers supply present positive IDs, nonnegative cursors and, for pages, an unsorted
 * page zero of size 1 through 100. No snapshot or aggregate claim is implied.
 */
@Transactional(readOnly = true, propagation = Propagation.REQUIRED)
public interface WorkflowDefinitionVersionRepository extends Repository<WorkflowDefinitionVersionEntity, Long> {

    Optional<WorkflowDefinitionVersionEntity> findById(Long id);

    Optional<WorkflowDefinitionVersionEntity> findByDefinitionIdAndVersion(Long definitionId, Integer version);
}
