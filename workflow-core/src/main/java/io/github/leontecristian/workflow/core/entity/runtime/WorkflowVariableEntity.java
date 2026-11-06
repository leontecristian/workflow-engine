package io.github.leontecristian.workflow.core.entity.runtime;
import io.github.leontecristian.workflow.core.dto.runtime.BooleanLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.NullLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
import io.github.leontecristian.workflow.core.dto.runtime.TextLiteral;

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

/** Internal persistence state; not a supported host API. Controlled replacement is internal to claimed aggregate commands. */
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
    @JdbcTypeCode(SqlTypes.NVARCHAR)
    @Column(name = "json_value", columnDefinition = "nvarchar(max)")
    private String jsonValue;

    public WorkflowVariableEntity(long instanceId, String key, WorkflowValue value) {
        id=new WorkflowVariableId(instanceId,key); replace(value);
    }
    public void replace(WorkflowValue value) {
        valueType=value.type(); stringValue=null; longValue=null; decimalValue=null; booleanValue=null;
        dateValue=null; datetimeValue=null; jsonValue=null;
        if (value.value()==NullLiteral.INSTANCE) return;
        if (value.value() instanceof io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue json) { jsonValue=json.toJson(); return; }
        if (value.value() instanceof BooleanLiteral v) { booleanValue=v.value(); return; }
        String text=((TextLiteral)value.value()).value();
        switch (valueType) {
            case STRING -> stringValue=text;
            case LONG -> longValue=Long.valueOf(text);
            case DECIMAL -> decimalValue=new BigDecimal(text);
            case DATE -> dateValue=LocalDate.parse(text);
            case DATETIME -> datetimeValue=OffsetDateTime.parse(text);
            default -> throw new IllegalArgumentException("Unsupported value");
        }
    }
    public WorkflowValue executionValue() {
        if (valueType == VariableValueType.JSON) {
            try { return new WorkflowValue(valueType, jsonValue == null ? NullLiteral.INSTANCE
                    : io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue.parse(jsonValue)); }
            catch (RuntimeException failure) { throw new io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException(
                    io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category.HOST_CONFIGURATION); }
        }
        String text=switch(valueType) {
            case STRING -> stringValue;
            case LONG -> longValue==null ? null : longValue.toString();
            case DECIMAL -> decimalValue==null ? null : decimalValue.toPlainString();
            case DATE -> dateValue==null ? null : dateValue.toString();
            case DATETIME -> datetimeValue==null ? null : datetimeValue.toInstant().toString();
            case BOOLEAN -> null;
            default -> throw new IllegalStateException("Unsupported persisted scalar");
        };
        WorkflowLiteral token=NullLiteral.INSTANCE;
        if(text!=null) token=new TextLiteral(text);
        if(booleanValue!=null) token=new BooleanLiteral(booleanValue);
        return new WorkflowValue(valueType,token);
    }
    protected WorkflowVariableEntity() {
    }

}
