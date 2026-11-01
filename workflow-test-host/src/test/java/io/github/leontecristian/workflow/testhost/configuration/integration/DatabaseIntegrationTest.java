package io.github.leontecristian.workflow.testhost.configuration.integration;

import io.github.leontecristian.workflow.testhost.WorkflowTestHostApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/** Base for SQL Server tests; creates an application context without opening an HTTP port. */
@SpringBootTest(classes = WorkflowTestHostApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles(value = "integration-test", inheritProfiles = false)
@ContextConfiguration(initializers = IntegrationDatabaseInitializer.class)
public abstract class DatabaseIntegrationTest {
}
