package io.github.leontecristian.workflow.core.repository.runtime;

import io.github.leontecristian.workflow.core.entity.runtime.WorkflowInstanceEntity;
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
public interface WorkflowInstanceRepository extends Repository<WorkflowInstanceEntity, Long> {

    Optional<WorkflowInstanceEntity> findById(Long id);

}
