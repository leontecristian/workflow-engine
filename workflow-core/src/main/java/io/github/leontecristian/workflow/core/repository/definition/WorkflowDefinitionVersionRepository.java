package io.github.leontecristian.workflow.core.repository.definition;

import io.github.leontecristian.workflow.core.entity.definition.WorkflowDefinitionVersionEntity;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal reads only; results grant no authorization and must not escape into host responses.
 * Callers supply present positive IDs, nonnegative cursors and, for pages, an unsorted
 * page zero of size 1 through 100. No snapshot or aggregate claim is implied.
 */
@Transactional(readOnly = true, propagation = Propagation.REQUIRED)
public interface WorkflowDefinitionVersionRepository extends Repository<WorkflowDefinitionVersionEntity, Long> {

    @Query("""
            select v.id as id,
                   v.definitionId as definitionId,
                   v.version as version,
                   v.schemaVersion as schemaVersion,
                   v.status as status,
                   v.contentRevision as contentRevision,
                   v.lockVersion as lockVersion,
                   v.contentHash as contentHash,
                   v.canonicalizationVersion as canonicalizationVersion,
                   v.createdAt as createdAt,
                   v.createdBy as createdBy,
                   v.updatedAt as updatedAt,
                   v.updatedBy as updatedBy,
                   v.publishedAt as publishedAt,
                   v.publishedBy as publishedBy,
                   v.retiredAt as retiredAt,
                   v.retiredBy as retiredBy,
                   v.validationStatus as validationStatus,
                   v.validatedContentRevision as validatedContentRevision,
                   v.validatedContentHash as validatedContentHash,
                   v.validationContextHash as validationContextHash,
                   v.validatedAt as validatedAt,
                   v.validatedBy as validatedBy
            from WorkflowDefinitionVersionEntity v
            where v.definitionId = :definitionId and v.id > :afterId
              and (:status is null or v.status = :status)
            order by v.id asc
            """)
    List<DefinitionVersionRow> findSummaryPage(@Param("definitionId") Long definitionId,
                                             @Param("status") DefinitionStatus status,
                                             @Param("afterId") long afterId, Pageable pageable);

    Optional<WorkflowDefinitionVersionEntity> findById(Long id);

    Optional<WorkflowDefinitionVersionEntity> findByDefinitionIdAndVersion(Long definitionId, Integer version);
}
