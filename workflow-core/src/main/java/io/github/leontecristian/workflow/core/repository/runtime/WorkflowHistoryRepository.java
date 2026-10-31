package io.github.leontecristian.workflow.core.repository.runtime;

import io.github.leontecristian.workflow.core.entity.runtime.WorkflowHistoryEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowHistoryId;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
public interface WorkflowHistoryRepository extends Repository<WorkflowHistoryEntity, WorkflowHistoryId> {

    Optional<WorkflowHistoryEntity> findById(WorkflowHistoryId id);

    @Query("""
            select history from WorkflowHistoryEntity history
            where history.id.instanceId = :instanceId and history.id.sequenceNumber > :afterSequence
            order by history.id.sequenceNumber asc
            """)
    List<WorkflowHistoryEntity> findPageByInstanceId(@Param("instanceId") Long instanceId,
                                                  @Param("afterSequence") long afterSequence, Pageable pageable);

    @Query("""
            select history from WorkflowHistoryEntity history
            where history.taskId = :taskId and history.eventType = 'TASK_COMPLETED'
            """)
    Optional<WorkflowHistoryEntity> findCompletionByTaskId(@Param("taskId") Long taskId);
}
