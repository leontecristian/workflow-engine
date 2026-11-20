package io.github.leontecristian.workflow.core.entity.tasktype;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.type.BasicType;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskTypeMappingUnitTest {

    @Test
    void codeUsesVarcharWhenHostEnablesNationalizedStrings() {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.SQLServerDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.use_nationalized_character_data", true)
                .build();
        try {
            var entity = new MetadataSources(registry).addAnnotatedClass(TaskTypeEntity.class)
                    .buildMetadata().getEntityBinding(TaskTypeEntity.class.getName());
            var codeType = (BasicType<?>) entity.getProperty("code").getType();
            assertEquals(SqlTypes.VARCHAR, codeType.getJdbcType().getJdbcTypeCode());
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
