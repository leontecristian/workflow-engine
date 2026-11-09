package io.github.leontecristian.workflow.testhost.repository.sandbox;

import io.github.leontecristian.workflow.testhost.dto.sandbox.SandboxRequestView;
import io.github.leontecristian.workflow.testhost.dto.sandbox.SandboxRequestSummary;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue;
import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import io.github.leontecristian.workflow.testhost.exception.console.SandboxRequestConflict;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
public class SandboxRequestStore {
    private final JdbcTemplate jdbc;

    public SandboxRequestStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SandboxRequestView create(String name, String actor, Instant time) {
        return create(name, null, actor, time);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SandboxRequestView create(String name, String requestDataJson, String actor, Instant time) {
        if (name == null || name.isBlank() || name.length() > 200) throw ConsoleException.invalid();
        String data = requestDataJson == null ? null : WorkflowJsonValue.parse(requestDataJson).toJson();
        return jdbc.queryForObject(
                "INSERT INTO wf_test.sandbox_request(name,created_by,created_at,lock_version,request_data_json)"
                        + " OUTPUT inserted.* VALUES(?,?,?,0,?)",
                this::map,
                name,
                actor,
                utc(time), data);
    }

    public SandboxRequestView find(long id) {
        return jdbc
                .query("SELECT * FROM wf_test.sandbox_request WHERE id=?", this::map, id)
                .stream()
                .findFirst()
                .orElseThrow(ConsoleException::missing);
    }

    public List<SandboxRequestSummary> page(long after, int count) {
        return jdbc.query(
                "SELECT TOP (?) id,name,status,created_by,created_at,lock_version,instance_id"
                        + " FROM wf_test.sandbox_request WHERE id>? ORDER BY id",
                (rs, row) -> new SandboxRequestSummary(rs.getLong("id"), rs.getString("name"),
                        rs.getString("status"), rs.getString("created_by"), instant(rs, "created_at"),
                        rs.getLong("lock_version"), rs.getObject("instance_id", Long.class)),
                count,
                after);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void link(long id, long expected, long instanceId) {
        if (expected == Long.MAX_VALUE
                || jdbc.update(
                                "UPDATE wf_test.sandbox_request SET"
                                    + " instance_id=?,lock_version=lock_version+1 WHERE id=? AND"
                                    + " instance_id IS NULL AND lock_version=?",
                                instanceId,
                                id,
                                expected)
                        != 1) throw new SandboxRequestConflict();
    }

    public boolean linkedToInstance(long instanceId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT CAST(CASE WHEN EXISTS (SELECT 1 FROM wf_test.sandbox_request WHERE instance_id=?)"
                        + " THEN 1 ELSE 0 END AS bit)",
                Boolean.class,
                instanceId));
    }

    public boolean linked(long id, long instanceId) {
        return jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wf_test.sandbox_request WHERE id=? AND instance_id=?",
                        Integer.class,
                        id,
                        instanceId)
                == 1;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateStatus(long id, long instanceId, long expectedVersion, String previous, String status) {
        if (expectedVersion == Long.MAX_VALUE
                || jdbc.update(
                        "UPDATE wf_test.sandbox_request SET status=?,lock_version=lock_version+1"
                                + " WHERE id=? AND instance_id=? AND lock_version=?"
                                + " AND status COLLATE Latin1_General_100_BIN2=?",
                        status, id, instanceId, expectedVersion, previous) != 1) {
            throw new SandboxRequestConflict();
        }
    }

    private SandboxRequestView map(ResultSet rs, int row) throws SQLException {
        return new SandboxRequestView(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("status"),
                rs.getString("created_by"),
                instant(rs, "created_at"),
                rs.getLong("lock_version"),
                rs.getObject("instance_id", Long.class),
                rs.getString("request_data_json"));
    }

    public static LocalDateTime utc(Instant time) {
        return LocalDateTime.ofInstant(time, ZoneOffset.UTC);
    }

    public static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
