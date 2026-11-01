package io.github.leontecristian.workflow.testhost.configuration.integration;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntegrationDatabaseGuardUnitTest {
    @Test
    void acceptsDedicatedConfigurationWithoutOpeningAConnection() {
        try (GenericApplicationContext context = context()) {
            new IntegrationDatabaseInitializer().initialize(context);
            assertThat(context.getBeanFactory().getSingleton("dataSource")).isInstanceOf(DataSource.class);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:sqlserver://localhost:1433;databaseName=apolo_workflow_dev",
            "jdbc:sqlserver://production:1433;databaseName=apolo_workflow_test",
            "jdbc:h2:mem:workflow",
            "jdbc:sqlserver://localhost:1433;databaseName=apolo_workflow_test;databaseName=apolo_workflow_dev"
    })
    void rejectsOtherTargetsBeforeRegisteringADataSource(String url) {
        try (GenericApplicationContext context = context()) {
            ((MockEnvironment) context.getEnvironment()).setProperty("spring.datasource.url", url);
            assertThatThrownBy(() -> new IntegrationDatabaseInitializer().initialize(context))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("spring.datasource.url");
            assertThat(context.getBeanFactory().containsSingleton("dataSource")).isFalse();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring.liquibase.url", "spring.datasource.jndi-name",
            "spring.datasource.hikari.jdbc-url", "spring.jpa.properties.hibernate.connection.url"})
    void rejectsAlternateConnectionRoutes(String key) {
        try (GenericApplicationContext context = context()) {
            ((MockEnvironment) context.getEnvironment()).setProperty(key, "alternate-target");
            assertThatThrownBy(() -> new IntegrationDatabaseInitializer().initialize(context))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining(key);
        }
    }

    @Test
    void rejectsMissingProfileAndDestructiveMigrationSetting() {
        try (GenericApplicationContext context = context()) {
            MockEnvironment environment = (MockEnvironment) context.getEnvironment();
            environment.setActiveProfiles("development");
            assertThatThrownBy(() -> new IntegrationDatabaseInitializer().initialize(context))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("profile");
            environment.setActiveProfiles("integration-test");
            environment.setProperty("spring.liquibase.drop-first", "true");
            assertThatThrownBy(() -> new IntegrationDatabaseInitializer().initialize(context))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("drop-first");
        }
    }

    private GenericApplicationContext context() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", IntegrationDatabaseInitializer.URL)
                .withProperty("spring.liquibase.change-log", "classpath:db/changelog/test-host/test-host-master.xml")
                .withProperty("spring.liquibase.drop-first", "false")
                .withProperty("spring.jpa.hibernate.ddl-auto", "validate");
        environment.setActiveProfiles("integration-test");
        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(environment);
        return context;
    }
}
