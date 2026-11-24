package io.github.leontecristian.workflow.core.repository.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowTaskEntity;
import java.time.LocalDateTime;
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

    String TASK_PAGE_COLUMNS = """
            select t.id as id, t.activityId as activityId, a.instanceId as instanceId,
                a.taskTypeId as taskTypeId, t.taskKey as taskKey, t.assignmentKind as assignmentKind, t.assignmentRef as assignmentRef, t.assigneeRef as assigneeRef,
                t.status as status, t.outcomeKey as outcomeKey, t.createdAt as createdAt,
                t.dueAt as dueAt, t.closedAt as closedAt, t.closedBy as closedBy,
                t.closureReason as closureReason
            """;
    String TASK_PAGE_FROM = """
            from WorkflowTaskEntity t join WorkflowActivityEntity a on a.id = t.activityId
            """;
    String TASK_PAGE_SELECT = TASK_PAGE_COLUMNS + TASK_PAGE_FROM;
    String TASK_METADATA_PAGE_SELECT = TASK_PAGE_COLUMNS + """
            , a.nodeKey as nodeKey, tt.code as taskTypeCode, tt.name as taskTypeName, i.definitionVersionId as definitionVersionId
            """ + TASK_PAGE_FROM + """
            join TaskTypeEntity tt on tt.id = a.taskTypeId join WorkflowInstanceEntity i on i.id = a.instanceId
            """;
    String TASK_FILTERS = """
              and (:status is null or t.status = :status)
              and (:taskType is null or (tt.code = :taskType and function('datalength', tt.code) = :taskTypeBytes))
              and (:kind is null or t.assignmentKind = :kind)
              and (:ref is null or (t.assignmentRef = :ref and function('datalength', t.assignmentRef) = :refBytes))
              and (:assignee is null or (t.assigneeRef = :assignee and function('datalength', t.assigneeRef) = :assigneeBytes))
              and (:instanceId is null or a.instanceId = :instanceId)
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

    @Query(TASK_METADATA_PAGE_SELECT + """
            where (:afterId = 0 or t.createdAt < :afterCreatedAt
                  or (t.createdAt = :afterCreatedAt and t.id < :afterId))
              and (
                (t.status = io.github.leontecristian.workflow.core.dto.runtime.TaskStatus.OPEN
                  and (t.assignmentKind <> io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind.USER
                    or (t.assignmentRef = :actorRef and function('datalength', t.assignmentRef) = :actorRefBytes)))
                or (t.status in (io.github.leontecristian.workflow.core.dto.runtime.TaskStatus.COMPLETED,
                                 io.github.leontecristian.workflow.core.dto.runtime.TaskStatus.CANCELLED)
                  and ((t.assigneeRef = :actorRef and function('datalength', t.assigneeRef) = :actorRefBytes)
                    or (t.assignmentKind = io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind.USER
                      and t.assignmentRef = :actorRef and function('datalength', t.assignmentRef) = :actorRefBytes)
                    or (t.status = io.github.leontecristian.workflow.core.dto.runtime.TaskStatus.COMPLETED
                      and t.closedBy = :actorRef and function('datalength', t.closedBy) = :actorRefBytes)))
              )
            """ + TASK_FILTERS + """
            order by t.createdAt desc, t.id desc
            """)
    List<RuntimeTaskAdministrationRow> findPersonalCandidatesPage(
            @Param("actorRef") String actorRef, @Param("actorRefBytes") int actorRefBytes,
            @Param("status") TaskStatus status, @Param("taskType") String taskType, @Param("taskTypeBytes") int taskTypeBytes,
            @Param("kind") AssignmentTargetKind kind, @Param("ref") String ref, @Param("refBytes") int refBytes,
            @Param("assignee") String assignee, @Param("assigneeBytes") int assigneeBytes,
            @Param("instanceId") Long instanceId, @Param("afterCreatedAt") LocalDateTime afterCreatedAt,
            @Param("afterId") long afterId, Pageable pageable);

    @Query("""
            select t from WorkflowTaskEntity t where t.activityId = :activityId
              and t.status = io.github.leontecristian.workflow.core.dto.runtime.TaskStatus.COMPLETED
              and t.id > :afterId order by t.id asc
            """)
    List<WorkflowTaskEntity> findCompletedPage(@Param("activityId") Long activityId,
                                              @Param("afterId") long afterId, Pageable pageable);

    @Query(TASK_METADATA_PAGE_SELECT + """
            where t.id > :afterId
            """ + TASK_FILTERS + """
            order by t.id asc
            """)
    List<RuntimeTaskAdministrationRow> findAdministrationPage(
            @Param("status") TaskStatus status,
            @Param("taskType") String taskType, @Param("taskTypeBytes") int taskTypeBytes,
            @Param("kind") AssignmentTargetKind kind,
            @Param("ref") String ref, @Param("refBytes") int refBytes,
            @Param("assignee") String assignee, @Param("assigneeBytes") int assigneeBytes,
            @Param("instanceId") Long instanceId, @Param("afterId") long afterId, Pageable pageable);

    Optional<WorkflowTaskEntity> findById(Long id);

    @Query("""
            select t.id from WorkflowTaskEntity t where t.activityId in :activityIds
              and t.status = io.github.leontecristian.workflow.core.dto.runtime.TaskStatus.COMPLETED
              and (select count(h) from WorkflowHistoryEntity h
                where h.taskId = t.id and h.eventType = 'TASK_COMPLETED') <> 1
            """)
    List<Long> findPreviousTasksWithoutUniqueFact(@Param("activityIds") java.util.Collection<Long> activityIds,
            Pageable page);

    @Query("""
            select task from WorkflowTaskEntity task
            where task.activityId = :activityId and task.id > :afterId
            order by task.id asc
            """)
    List<WorkflowTaskEntity> findPageByActivityId(@Param("activityId") Long activityId,
                                               @Param("afterId") long afterId, Pageable pageable);
}
