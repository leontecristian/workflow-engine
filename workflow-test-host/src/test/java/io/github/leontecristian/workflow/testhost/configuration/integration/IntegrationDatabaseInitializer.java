package io.github.leontecristian.workflow.testhost.configuration.integration;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Refuses alternate targets before any Spring beans or Liquibase migrations run. */
public final class IntegrationDatabaseInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    static final String DATABASE = "apolo_workflow_test";
    static final String URL = "jdbc:sqlserver://localhost:1433;databaseName=" + DATABASE
            + ";integratedSecurity=true;authenticationScheme=NativeAuthentication"
            + ";encrypt=true;trustServerCertificate=true";
    private static final String CHANGELOG = "classpath:db/changelog/test-host/test-host-master.xml";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        Environment environment = context.getEnvironment();
        if (!Arrays.asList(environment.getActiveProfiles()).contains("integration-test")) {
            throw new IllegalStateException("Database tests require the integration-test profile");
        }
        require(environment, "spring.datasource.url", URL);
        require(environment, "spring.liquibase.change-log", CHANGELOG);
        require(environment, "spring.liquibase.drop-first", "false");
        require(environment, "spring.jpa.hibernate.ddl-auto", "validate");
        for (String key : new String[] {
                "spring.liquibase.url", "spring.liquibase.user", "spring.liquibase.password",
                "spring.datasource.jndi-name", "spring.datasource.hikari.jdbc-url",
                "spring.jpa.properties.hibernate.connection.url",
                "spring.jpa.properties.jakarta.persistence.jdbc.url"}) {
            if (environment.containsProperty(key) && !environment.getProperty(key, "").isBlank()) {
                throw new IllegalStateException("Database tests reject alternate connection property: " + key);
            }
        }
        // A fixed test-only DataSource prevents pool/connection-property overrides from redirecting writes.
        context.getBeanFactory().registerSingleton("dataSource", new TestDataSource());
    }

    private static void require(Environment environment, String key, String expected) {
        if (!expected.equals(environment.getProperty(key))) {
            throw new IllegalStateException("Unsafe integration-test configuration: " + key
                    + ". Use the dedicated localhost apolo_workflow_test configuration.");
        }
    }

    private static final class TestDataSource extends AbstractDataSource {
        private final DriverManagerDataSource delegate = new DriverManagerDataSource(URL);

        @Override
        public Connection getConnection() throws SQLException {
            Connection connection = delegate.getConnection();
            try {
                if (!DATABASE.equals(connection.getCatalog())) {
                    throw new SQLException("Integration tests connected to an unexpected database");
                }
                return connection;
            } catch (SQLException failure) {
                try {
                    connection.close();
                } catch (SQLException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
                throw failure;
            }
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("Integration tests use Windows authentication only");
        }
    }
}
