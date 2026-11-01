package io.github.leontecristian.workflow.testhost;

import io.github.leontecristian.workflow.testhost.configuration.integration.DatabaseIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Harness acceptance only; feature-specific rollback/replay/concurrency tests remain separate. */
class DatabaseInfrastructureIT extends DatabaseIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void migratesRealSchemaAndConnectsOnlyToTestDatabase() {
        assertThat(jdbc.queryForObject("SELECT DB_NAME()", String.class)).isEqualTo("apolo_workflow_test");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.DATABASECHANGELOG", Integer.class))
                .isGreaterThan(0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s "
                + "ON s.schema_id=t.schema_id WHERE s.name='wf'", Integer.class)).isGreaterThanOrEqualTo(9);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.DATABASECHANGELOGLOCK WHERE LOCKED=1",
                Integer.class)).isZero();
    }

    @Test
    void committedFixtureIsVisibleOutsideTheTransactionAndCleanedUp() {
        String correlation = UUID.randomUUID().toString();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> insertAudit(correlation));
            assertThat(countAudit(correlation)).isEqualTo(1);
        } finally {
            deleteAudit(correlation);
        }
        assertThat(countAudit(correlation)).isZero();
    }

    @Test
    void failedTransactionRollsBackItsWrite() {
        String correlation = UUID.randomUUID().toString();
        try {
            assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                insertAudit(correlation);
                assertThat(countAudit(correlation)).isEqualTo(1);
                throw new IllegalStateException("intentional integration-test failure");
            })).isInstanceOf(IllegalStateException.class).hasMessage("intentional integration-test failure");
            assertThat(countAudit(correlation)).isZero();
        } finally {
            deleteAudit(correlation);
        }
    }

    private void insertAudit(String correlation) {
        jdbc.update("INSERT INTO wf_test.management_audit "
                + "(occurred_at,actor_ref,operation,entity_kind,entity_id,correlation_id,evidence_json) "
                + "VALUES (SYSUTCDATETIME(),?,'TEST','TASK_TYPE',1,?,N'{}')",
                "integration-test.infrastructure", correlation);
    }

    private int countAudit(String correlation) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM wf_test.management_audit "
                + "WHERE correlation_id=? AND actor_ref=?", Integer.class,
                correlation, "integration-test.infrastructure");
    }

    private void deleteAudit(String correlation) {
        jdbc.update("DELETE FROM wf_test.management_audit WHERE correlation_id=? AND actor_ref=?",
                correlation, "integration-test.infrastructure");
    }
}
