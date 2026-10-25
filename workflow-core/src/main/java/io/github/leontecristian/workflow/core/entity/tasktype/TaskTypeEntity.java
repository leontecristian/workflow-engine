package io.github.leontecristian.workflow.core.entity.tasktype;

import io.github.leontecristian.workflow.core.dto.tasktype.TaskTypeView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.Nationalized;

/**
 * Internal persistence model; catalog callers use detached TaskTypeView snapshots.
 * Public construction/mutation/snapshot access serves the service implementation across layers,
 * and does not make this entity a supported host integration API.
 */
@Entity
@Table(name = "workflow_task_type", schema = "wf")
public class TaskTypeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "code", nullable = false, length = 100, updatable = false)
    private String code;

    @Nationalized
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Nationalized
    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "selectable", nullable = false)
    private boolean selectable;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;

    protected TaskTypeEntity() {
    }

    public TaskTypeEntity(String code, String name, String description, boolean selectable) {
        this.code = code;
        replaceMetadata(name, description, selectable);
    }

    public void replaceMetadata(String name, String description, boolean selectable) {
        this.name = name;
        this.description = description;
        this.selectable = selectable;
    }

    public TaskTypeView snapshot() {
        return new TaskTypeView(id, code, name, description, selectable, lockVersion);
    }
}
