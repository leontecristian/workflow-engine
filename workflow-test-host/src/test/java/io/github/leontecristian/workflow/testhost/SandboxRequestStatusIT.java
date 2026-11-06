package io.github.leontecristian.workflow.testhost;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.leontecristian.workflow.testhost.configuration.integration.DatabaseIntegrationTest;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Real host-column defaults and projections on the guarded test database only. */
class SandboxRequestStatusIT extends DatabaseIntegrationTest {
    @Autowired private SandboxRequestStore requests;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;

    @Test
    void statusDefaultsToDraftAndIsReturnedByCreateDetailAndPage() {
        String marker = "request-status." + UUID.randomUUID();
        try {
            var created = new TransactionTemplate(transactions)
                    .execute(tx -> requests.create(marker, marker, Instant.now()));
            assertThat(created.status()).isEqualTo("DRAFT");
            assertThat(created.lockVersion()).isZero();
            assertThat(created.instanceId()).isNull();
            assertThat(requests.find(created.id()).status()).isEqualTo("DRAFT");
            assertThat(requests.page(created.id() - 1, 1).get(0).status()).isEqualTo("DRAFT");
            assertThat(new ConsoleWire().json(created)).contains("\"status\":\"DRAFT\"");
            assertThat(jdbc.queryForObject(
                    "SELECT is_nullable FROM sys.columns WHERE object_id=OBJECT_ID('wf_test.sandbox_request') AND name='status'",
                    Boolean.class)).isFalse();
        } finally {
            jdbc.update("DELETE FROM wf_test.sandbox_request WHERE name=? AND created_by=?", marker, marker);
        }
    }

    @Test
    void storedBusinessStatusIsIndependentOfWorkflowLinkAndRequestVersion() {
        String marker = "request-status." + UUID.randomUUID();
        try {
            var created = new TransactionTemplate(transactions)
                    .execute(tx -> requests.create(marker, marker, Instant.now()));
            // Test-owned SQL fixture only: no status mutation endpoint or lifecycle is introduced.
            jdbc.update("UPDATE wf_test.sandbox_request SET status=? WHERE id=? AND created_by=?",
                    "TEST_BOOKING_STATE", created.id(), marker);
            var beforeLink = requests.find(created.id());
            assertThat(beforeLink.status()).isEqualTo("TEST_BOOKING_STATE");
            assertThat(beforeLink.lockVersion()).isZero();
            new TransactionTemplate(transactions).executeWithoutResult(
                    tx -> requests.link(created.id(), 0, Long.MAX_VALUE - created.id()));
            var linked = requests.find(created.id());
            assertThat(linked.status()).isEqualTo("TEST_BOOKING_STATE");
            assertThat(linked.lockVersion()).isEqualTo(1);
            assertThat(linked.instanceId()).isEqualTo(Long.MAX_VALUE - created.id());
            assertThat(requests.page(created.id() - 1, 1).get(0).status()).isEqualTo("TEST_BOOKING_STATE");
        } finally {
            jdbc.update("DELETE FROM wf_test.sandbox_request WHERE name=? AND created_by=?", marker, marker);
        }
    }
}
