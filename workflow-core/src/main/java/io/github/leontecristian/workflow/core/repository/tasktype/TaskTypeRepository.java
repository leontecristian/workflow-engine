package io.github.leontecristian.workflow.core.repository.tasktype;

import io.github.leontecristian.workflow.core.entity.tasktype.TaskTypeEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Internal read repository: no inherited save, merge or delete surface. */
public interface TaskTypeRepository extends Repository<TaskTypeEntity, Long> {

    Optional<TaskTypeEntity> findById(Long id);

    Optional<TaskTypeEntity> findByCode(String code);

    @Query("""
            select taskType from TaskTypeEntity taskType
            where taskType.id > :afterId
              and (:selectable is null or taskType.selectable = :selectable)
            order by taskType.id asc
            """)
    List<TaskTypeEntity> findPage(@Param("afterId") long afterId,
                                  @Param("selectable") Boolean selectable, Pageable pageable);
}
