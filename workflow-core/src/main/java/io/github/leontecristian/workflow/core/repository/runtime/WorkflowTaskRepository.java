package io.github.leontecristian.workflow.core.repository.runtime;

import io.github.leontecristian.workflow.core.entity.runtime.WorkflowTaskEntity;
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
public interface WorkflowTaskRepository extends Repository<WorkflowTaskEntity, Long> {

    String TASK_PAGE_SELECT = """
            select t.id as id, t.activityId as activityId, a.instanceId as instanceId,
                a.taskTypeId as taskTypeId, t.taskKey as taskKey, t.assignmentKind as assignmentKind, t.assignmentRef as assignmentRef, t.assigneeRef as assigneeRef,
                t.status as status, t.outcomeKey as outcomeKey, t.createdAt as createdAt,
                t.dueAt as dueAt, t.closedAt as closedAt, t.closedBy as closedBy,
                t.closureReason as closureReason
            from WorkflowTaskEntity t join WorkflowActivityEntity a on a.id = t.activityId
            """;

    @Query(TASK_PAGE_SELECT + """
            where a.instanceId = :instanceId and t.id > :afterId order by t.id asc
            """)
    List<RuntimeTaskRow> findByInstancePage(@Param("instanceId") Long instanceId,
                                               @Param("afterId") long afterId, Pageable pageable);

    // SQL Server equality pads trailing spaces, including under BIN2. The byte count is
    // derived from validated UTF-16 input, not the driver's VARCHAR/NVARCHAR parameter binding.
    @Query(TASK_PAGE_SELECT + """
            where t.assignmentKind = io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind.USER
              and t.assignmentRef = :assignee
              and function('datalength', t.assignmentRef) = :assigneeBytes
              and t.status = io.github.leontecristian.workflow.core.dto.runtime.TaskStatus.OPEN
              and t.id > :afterId order by t.id asc
            """)
    List<RuntimeTaskRow> findAssignedPage(@Param("assignee") String assignee,
                                             @Param("assigneeBytes") int assigneeBytes,
                                             @Param("afterId") long afterId, Pageable pageable);

    @Query(TASK_PAGE_SELECT + """
            where t.status = io.github.leontecristian.workflow.core.dto.runtime.TaskStatus.OPEN
              and (t.assignmentKind <> io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind.USER
                or (t.assignmentRef = :actorRef and function('datalength', t.assignmentRef) = :actorRefBytes))
              and t.id > :afterId order by t.id asc
            """)
    List<RuntimeTaskRow> findEligibleCandidatesPage(@Param("actorRef") String actorRef,
            @Param("actorRefBytes") int actorRefBytes, @Param("afterId") long afterId, Pageable pageable);

    @Query("""
            select t from WorkflowTaskEntity t where t.activityId = :activityId
              and t.status = io.github.leontecristian.workflow.core.dto.runtime.TaskStatus.COMPLETED
              and t.id > :afterId order by t.id asc
            """)
    List<WorkflowTaskEntity> findCompletedPage(@Param("activityId") Long activityId,
                                              @Param("afterId") long afterId, Pageable pageable);

    Optional<WorkflowTaskEntity> findById(Long id);

    @Query("""
            select task from WorkflowTaskEntity task
            where task.activityId = :activityId and task.id > :afterId
            order by task.id asc
            """)
    List<WorkflowTaskEntity> findPageByActivityId(@Param("activityId") Long activityId,
                                               @Param("afterId") long afterId, Pageable pageable);
}
