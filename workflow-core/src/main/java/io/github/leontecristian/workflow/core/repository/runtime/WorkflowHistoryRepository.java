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

    @Query("""
            select h.id.instanceId as instanceId, h.id.sequenceNumber as historySequence,
                h.eventType as eventType, h.commandId as commandId, h.correlationId as correlationId,
                h.actorKind as actorKind, h.actorRef as actorRef, h.occurredAt as occurredAt,
                h.activityId as activityId, h.taskId as taskId, h.sourceNodeKey as sourceNodeKey,
                h.targetNodeKey as targetNodeKey, h.transitionKey as transitionKey,
                h.detailsSchemaVersion as detailsSchemaVersion
            from WorkflowHistoryEntity h where h.id.instanceId = :instanceId
              and h.id.sequenceNumber > :afterSequence order by h.id.sequenceNumber asc
            """)
    List<RuntimeHistoryRow> findSummaryPage(
            @Param("instanceId") Long instanceId, @Param("afterSequence") long afterSequence, Pageable pageable);

    Optional<WorkflowHistoryEntity> findById(WorkflowHistoryId id);

    @Query("""
            select new io.github.leontecristian.workflow.core.repository.runtime.PreviousDecisionRow(t,h)
            from WorkflowTaskEntity t join WorkflowHistoryEntity h on h.taskId = t.id
            where t.activityId in :activityIds
              and t.status = io.github.leontecristian.workflow.core.dto.runtime.TaskStatus.COMPLETED
              and h.eventType = 'TASK_COMPLETED' and h.id.sequenceNumber > :afterSequence
            order by h.id.sequenceNumber
            """)
    List<PreviousDecisionRow> findPreviousDecisionPage(@Param("activityIds") java.util.Collection<Long> activityIds,
            @Param("afterSequence") long afterSequence, Pageable page);

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

    @Query("select h from WorkflowHistoryEntity h where h.taskId = :taskId and h.eventType = 'TASK_CREATED'")
    Optional<WorkflowHistoryEntity> findCreationByTaskId(@Param("taskId") Long taskId);

    @Query("select h from WorkflowHistoryEntity h where h.activityId = :activityId and h.eventType = :eventType order by h.id.sequenceNumber")
    List<WorkflowHistoryEntity> findActivityFacts(@Param("activityId") Long activityId,
            @Param("eventType") String eventType, Pageable pageable);
}
