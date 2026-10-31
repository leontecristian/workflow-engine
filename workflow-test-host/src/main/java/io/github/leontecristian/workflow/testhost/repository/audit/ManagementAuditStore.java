package io.github.leontecristian.workflow.testhost.repository.audit;

import io.github.leontecristian.workflow.testhost.dto.audit.AuditView;
import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class ManagementAuditStore {
    private final JdbcTemplate jdbc;

    public ManagementAuditStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(
            Instant time,
            String actor,
            String operation,
            String kind,
            long entity,
            UUID correlation,
            String evidence) {
        jdbc.update(
                "INSERT INTO"
                    + " wf_test.management_audit(occurred_at,actor_ref,operation,entity_kind,entity_id,correlation_id,evidence_json)"
                    + " VALUES(?,?,?,?,?,?,?)",
                SandboxRequestStore.utc(time),
                actor,
                operation,
                kind,
                entity,
                correlation.toString(),
                evidence);
    }

    public List<AuditView> page(long after, int count) {
        return jdbc.query(
                "SELECT TOP (?)"
                    + " id,occurred_at,actor_ref,operation,entity_kind,entity_id,correlation_id"
                    + " FROM wf_test.management_audit WHERE id>? ORDER BY id",
                (rs, n) -> map(rs, false),
                count,
                after);
    }

    public AuditView find(long id) {
        return jdbc
                .query(
                        "SELECT * FROM wf_test.management_audit WHERE id=?",
                        (rs, n) -> map(rs, true),
                        id)
                .stream()
                .findFirst()
                .orElseThrow(ConsoleException::missing);
    }

    private AuditView map(ResultSet rs, boolean evidence) throws SQLException {
        return new AuditView(
                rs.getLong("id"),
                SandboxRequestStore.instant(rs, "occurred_at"),
                rs.getString("actor_ref"),
                rs.getString("operation"),
                rs.getString("entity_kind"),
                rs.getLong("entity_id"),
                UUID.fromString(rs.getString("correlation_id")),
                evidence ? rs.getString("evidence_json") : null);
    }
}
