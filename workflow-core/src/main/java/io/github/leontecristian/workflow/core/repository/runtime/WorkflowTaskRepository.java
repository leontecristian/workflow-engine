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

    Optional<WorkflowTaskEntity> findById(Long id);

    @Query("""
            select task from WorkflowTaskEntity task
            where task.activityId = :activityId and task.id > :afterId
            order by task.id asc
            """)
    List<WorkflowTaskEntity> findPageByActivityId(@Param("activityId") Long activityId,
                                               @Param("afterId") long afterId, Pageable pageable);
}
