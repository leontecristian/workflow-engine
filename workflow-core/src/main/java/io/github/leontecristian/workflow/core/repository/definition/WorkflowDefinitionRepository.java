package io.github.leontecristian.workflow.core.repository.definition;

import io.github.leontecristian.workflow.core.entity.definition.WorkflowDefinitionEntity;
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
public interface WorkflowDefinitionRepository extends Repository<WorkflowDefinitionEntity, Long> {

    @org.springframework.data.jpa.repository.Query("select d from WorkflowDefinitionEntity d where d.id > :afterId order by d.id asc")
    java.util.List<WorkflowDefinitionEntity> findPage(@org.springframework.data.repository.query.Param("afterId") long afterId,
                                                    org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("select d from WorkflowDefinitionEntity d where (:afterId = 0 or d.id < :afterId) order by d.id desc")
    java.util.List<WorkflowDefinitionEntity> findNewestPage(@org.springframework.data.repository.query.Param("afterId") long afterId,
                                                          org.springframework.data.domain.Pageable pageable);

    Optional<WorkflowDefinitionEntity> findById(Long id);

    Optional<WorkflowDefinitionEntity> findByWorkflowKey(String workflowKey);
}
