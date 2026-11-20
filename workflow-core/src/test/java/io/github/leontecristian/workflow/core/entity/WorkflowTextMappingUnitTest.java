package io.github.leontecristian.workflow.core.entity;

import io.github.leontecristian.workflow.core.entity.definition.WorkflowDefinitionEntity;
import io.github.leontecristian.workflow.core.entity.definition.WorkflowDefinitionVersionEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowActivityEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowCommandReceiptEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowHistoryEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowInstanceEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowTaskEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowVariableEntity;
import io.github.leontecristian.workflow.core.entity.tasktype.TaskTypeEntity;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.Column;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkflowTextMappingUnitTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void textColumnsMatchTheSqlServerSchema(boolean nationalized) {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.SQLServerDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.use_nationalized_character_data", nationalized)
                .build();
        try {
            var sources = new MetadataSources(registry);
            sources.addAnnotatedClass(WorkflowDefinitionEntity.class);
            sources.addAnnotatedClass(WorkflowDefinitionVersionEntity.class);
            sources.addAnnotatedClass(WorkflowActivityEntity.class);
            sources.addAnnotatedClass(WorkflowCommandReceiptEntity.class);
            sources.addAnnotatedClass(WorkflowHistoryEntity.class);
            sources.addAnnotatedClass(WorkflowInstanceEntity.class);
            sources.addAnnotatedClass(WorkflowTaskEntity.class);
            sources.addAnnotatedClass(WorkflowVariableEntity.class);
            sources.addAnnotatedClass(TaskTypeEntity.class);
            var metadata = sources.buildMetadata();
            assertColumns(metadata, "workflow_activity", SqlTypes.VARCHAR, "closure_reason completion_policy contribution_outcome_key node_key outcome_key status");
            assertColumns(metadata, "workflow_activity", SqlTypes.NVARCHAR, "closed_by review_revision_ref");
            assertColumns(metadata, "workflow_command_receipt", SqlTypes.VARCHAR, "actor_kind fingerprint_version operation_type");
            assertColumns(metadata, "workflow_command_receipt", SqlTypes.NVARCHAR, "actor_ref result_json");
            assertColumns(metadata, "workflow_definition", SqlTypes.VARCHAR, "workflow_key");
            assertColumns(metadata, "workflow_definition", SqlTypes.NVARCHAR, "catalog_description catalog_name created_by");
            assertColumns(metadata, "workflow_definition_version", SqlTypes.VARCHAR, "canonicalization_version status validation_status");
            assertColumns(metadata, "workflow_definition_version", SqlTypes.NVARCHAR, "created_by definition_json published_by retired_by updated_by validated_by validation_details_json");
            assertColumns(metadata, "workflow_history", SqlTypes.VARCHAR, "actor_kind event_type source_node_key target_node_key transition_key");
            assertColumns(metadata, "workflow_history", SqlTypes.NVARCHAR, "actor_ref correlation_id details_json");
            assertColumns(metadata, "workflow_instance", SqlTypes.VARCHAR, "business_type current_node_key result_key status");
            assertColumns(metadata, "workflow_instance", SqlTypes.NVARCHAR, "business_id ended_by started_by termination_reason");
            assertColumns(metadata, "workflow_task", SqlTypes.VARCHAR, "assignment_kind closure_reason outcome_key status task_key");
            assertColumns(metadata, "workflow_task", SqlTypes.NVARCHAR, "assignee_ref assignment_ref closed_by");
            assertColumns(metadata, "workflow_task_type", SqlTypes.VARCHAR, "code");
            assertColumns(metadata, "workflow_task_type", SqlTypes.NVARCHAR, "description name");
            assertColumns(metadata, "workflow_variable", SqlTypes.VARCHAR, "value_type variable_key");
            assertColumns(metadata, "workflow_variable", SqlTypes.NVARCHAR, "json_value string_value");
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private static void assertColumns(Metadata metadata, String tableName, int jdbcType, String names) {
        var table = metadata.collectTableMappings().stream()
                .filter(candidate -> candidate.getName().equals(tableName))
                .findFirst().orElseThrow();
        for (String name : names.split(" ")) {
            var column = table.getColumn(new Column(name));
            assertEquals(jdbcType, column.getSqlTypeCode(metadata), tableName + "." + name);
        }
    }
}
