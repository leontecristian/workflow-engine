package io.github.leontecristian.workflow.core.repository.runtime;

import io.github.leontecristian.workflow.core.entity.runtime.WorkflowInstanceEntity;
import io.github.leontecristian.workflow.core.dto.runtime.InstanceStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal reads only; results grant no authorization and must not escape into host responses.
 * Callers supply present positive IDs, nonnegative cursors and, for pages, an unsorted
 * page zero with at most 101 rows (100 results plus lookahead). No snapshot or aggregate claim is implied.
 */
@Transactional(readOnly = true, propagation = Propagation.REQUIRED)
public interface WorkflowInstanceRepository extends Repository<WorkflowInstanceEntity, Long> {

    Optional<WorkflowInstanceEntity> findById(Long id);

    @Query("""
            select i.id as id, i.definitionVersionId as definitionVersionId,
                d.workflowKey as definitionKey, v.version as definitionVersion,
                i.status as status, i.currentNodeKey as currentNodeKey, i.resultKey as resultKey,
                i.businessType as businessType, i.businessId as businessId,
                i.startedAt as startedAt, i.startedBy as startedBy, i.endedAt as endedAt,
                i.lastHistorySequence as lastHistorySequence, i.lockVersion as lockVersion
            from WorkflowInstanceEntity i
                join WorkflowDefinitionVersionEntity v on v.id = i.definitionVersionId
                join WorkflowDefinitionEntity d on d.id = v.definitionId
            where i.id > :afterId and (:status is null or i.status = :status)
            order by i.id asc
            """)
    List<RuntimeInstanceRow> findSummaryPage(@Param("status") InstanceStatus status,
            @Param("afterId") long afterId, Pageable pageable);

}
