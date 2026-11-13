package io.github.leontecristian.workflow.core.repository.runtime;

import io.github.leontecristian.workflow.core.entity.runtime.WorkflowActivityEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal reads only; results grant no authorization and must not escape into host responses.
 * Callers supply present positive IDs, nonnegative cursors and, for pages, an unsorted
 * page zero of size 1 through 100. No snapshot or aggregate claim is implied.
 */
@Transactional(readOnly = true, propagation = Propagation.REQUIRED)
public interface WorkflowActivityRepository extends Repository<WorkflowActivityEntity, Long> {

    Optional<WorkflowActivityEntity> findById(Long id);

    @Query("""
            select a from WorkflowActivityEntity a
            where a.instanceId = :instanceId and a.id > :afterId and a.id <> :receivingId
              and a.nodeKey in :nodeKeys
              and (exists (select h.id.sequenceNumber from WorkflowHistoryEntity h
                  where h.activityId = a.id and h.eventType = 'ACTIVITY_COMPLETED'
                    and h.id.sequenceNumber < :cutoff)
                or (a.status = io.github.leontecristian.workflow.core.dto.runtime.ActivityStatus.COMPLETED
                  and not exists (select h.id.sequenceNumber from WorkflowHistoryEntity h
                    where h.activityId = a.id and h.eventType = 'ACTIVITY_COMPLETED')))
            order by a.id
            """)
    List<WorkflowActivityEntity> findPreviousSources(@Param("instanceId") long instanceId,
            @Param("receivingId") long receivingId, @Param("nodeKeys") java.util.Collection<String> nodeKeys,
            @Param("cutoff") long cutoff, @Param("afterId") long afterId, Pageable page);

    List<WorkflowActivityEntity> findByInstanceIdAndNodeKey(Long instanceId, String nodeKey, Pageable page);

    @Query("""
            select activity from WorkflowActivityEntity activity
            where activity.instanceId = :instanceId
              and activity.status = io.github.leontecristian.workflow.core.dto.runtime.ActivityStatus.ACTIVE
            """)
    Optional<WorkflowActivityEntity> findActiveByInstanceId(@Param("instanceId") Long instanceId);
}
