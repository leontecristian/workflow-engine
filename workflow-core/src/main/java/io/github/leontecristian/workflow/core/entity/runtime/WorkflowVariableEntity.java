package io.github.leontecristian.workflow.core.entity.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import org.hibernate.annotations.FractionalSeconds;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;
import org.hibernate.type.SqlTypes;

/** Internal persistence state; not a supported host API. No mutation contract is provided. */
@Entity
@Table(name = "workflow_variable", schema = "wf")
@Getter
public class WorkflowVariableEntity {

    @EmbeddedId
    private WorkflowVariableId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 12)
    private VariableValueType valueType;

    @Nationalized
    @Column(name = "string_value", length = 4000)
    private String stringValue;

    @Column(name = "long_value")
    private Long longValue;

    @Column(name = "decimal_value", precision = 38, scale = 10)
    private BigDecimal decimalValue;

    @Column(name = "boolean_value")
    private Boolean booleanValue;

    @Column(name = "date_value")
    private LocalDate dateValue;

    @FractionalSeconds(7)
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "datetime_value")
    private OffsetDateTime datetimeValue;

    @Nationalized
    @JdbcTypeCode(SqlTypes.LONG32NVARCHAR)
    @Column(name = "json_value", columnDefinition = "nvarchar(max)")
    private String jsonValue;

    protected WorkflowVariableEntity() {
    }

}
