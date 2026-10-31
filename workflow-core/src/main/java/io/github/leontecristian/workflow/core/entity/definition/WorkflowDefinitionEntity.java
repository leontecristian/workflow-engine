package io.github.leontecristian.workflow.core.entity.definition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import org.hibernate.annotations.FractionalSeconds;
import org.hibernate.annotations.Nationalized;

/** Internal persistence state; not a supported host API. No mutation contract is provided. */
@Entity
@Table(name = "workflow_definition", schema = "wf")
@Getter
public class WorkflowDefinitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "workflow_key", nullable = false, length = 100)
    private String workflowKey;

    @Nationalized
    @Column(name = "catalog_name", nullable = false, length = 200)
    private String catalogName;

    @Nationalized
    @Column(name = "catalog_description", length = 2000)
    private String catalogDescription;

    /** Stored UTC time, without JVM-default-zone conversion. */
    @FractionalSeconds(7)
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Nationalized
    @Column(name = "created_by", nullable = false, length = 200)
    private String createdBy;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;

    protected WorkflowDefinitionEntity() {
    }

}
