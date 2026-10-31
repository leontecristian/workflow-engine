package io.github.leontecristian.workflow.testhost.repository.event;

import io.github.leontecristian.workflow.core.dto.runtime.WorkflowEvent;
import io.github.leontecristian.workflow.testhost.dto.event.InboxView;
import io.github.leontecristian.workflow.testhost.dto.event.OutboxView;
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
public class ConsoleEventStore {
    private static final String SUMMARY =
            "id,event_id,instance_id,history_sequence,command_id,event_type,occurred_at,delivered_at,delivery_count";
    private final JdbcTemplate jdbc;

    public ConsoleEventStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(WorkflowEvent event, String json) {
        jdbc.update(
                "INSERT INTO"
                    + " wf_test.workflow_event_outbox(event_id,instance_id,history_sequence,command_id,event_type,occurred_at,event_json,delivery_count)"
                    + " VALUES(?,?,?,?,?,?,?,0)",
                event.eventId(),
                event.instanceId(),
                event.historySequence(),
                event.commandId().toString(),
                event.eventType(),
                SandboxRequestStore.utc(event.occurredAt()),
                json);
    }

    public List<OutboxView> page(String status, long after, int count) {
        String filter =
                switch (status) {
                    case "ALL" -> "";
                    case "PENDING" -> " AND delivered_at IS NULL";
                    case "DELIVERED" -> " AND delivered_at IS NOT NULL";
                    default -> throw ConsoleException.invalid();
                };
        return jdbc.query(
                "SELECT TOP (?) "
                        + SUMMARY
                        + " FROM wf_test.workflow_event_outbox WHERE id>?"
                        + filter
                        + " ORDER BY id",
                (rs, n) -> outbox(rs, false),
                count,
                after);
    }

    public OutboxView find(String eventId, boolean lock) {
        return jdbc
                .query(
                        "SELECT * FROM wf_test.workflow_event_outbox"
                                + (lock ? " WITH (UPDLOCK,HOLDLOCK,ROWLOCK)" : "")
                                + " WHERE event_id=? AND DATALENGTH(event_id)=?",
                        (rs, n) -> outbox(rs, true),
                        eventId,
                        eventId.length())
                .stream()
                .findFirst()
                .orElseThrow(ConsoleException::missing);
    }

    public List<OutboxView> pending(int limit) {
        return jdbc.query(
                "SELECT TOP (?) * FROM wf_test.workflow_event_outbox WITH"
                        + " (UPDLOCK,HOLDLOCK,ROWLOCK) WHERE delivered_at IS NULL ORDER BY id",
                (rs, n) -> outbox(rs, true),
                limit);
    }

    public List<InboxView> inbox(long after, int count) {
        return jdbc.query(
                "SELECT TOP (?) id,event_id,received_at FROM wf_test.workflow_event_inbox WHERE"
                        + " id>? ORDER BY id",
                (rs, n) -> inbox(rs, false),
                count,
                after);
    }

    public InboxView findInbox(String id) {
        return jdbc
                .query(
                        "SELECT * FROM wf_test.workflow_event_inbox WHERE event_id=? AND"
                                + " DATALENGTH(event_id)=?",
                        (rs, n) -> inbox(rs, true),
                        id,
                        id.length())
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insertInbox(String eventId, Instant time, String json) {
        jdbc.update(
                "INSERT INTO wf_test.workflow_event_inbox(event_id,received_at,event_json)"
                        + " VALUES(?,?,?)",
                eventId,
                SandboxRequestStore.utc(time),
                json);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void delivered(OutboxView event, Instant time) {
        if (event.deliveryCount() == Long.MAX_VALUE)
            throw new IllegalStateException("Delivery count exhausted");
        if (jdbc.update(
                        "UPDATE wf_test.workflow_event_outbox SET"
                            + " delivered_at=COALESCE(delivered_at,?),delivery_count=delivery_count+1"
                            + " WHERE id=? AND delivery_count=?",
                        SandboxRequestStore.utc(time),
                        event.id(),
                        event.deliveryCount())
                != 1) throw new IllegalStateException("Delivery claim lost");
    }

    private OutboxView outbox(ResultSet rs, boolean evidence) throws SQLException {
        return new OutboxView(
                rs.getLong("id"),
                rs.getString("event_id"),
                rs.getLong("instance_id"),
                rs.getLong("history_sequence"),
                UUID.fromString(rs.getString("command_id")),
                rs.getString("event_type"),
                SandboxRequestStore.instant(rs, "occurred_at"),
                SandboxRequestStore.instant(rs, "delivered_at"),
                rs.getLong("delivery_count"),
                evidence ? rs.getString("event_json") : null);
    }

    private InboxView inbox(ResultSet rs, boolean evidence) throws SQLException {
        return new InboxView(
                rs.getLong("id"),
                rs.getString("event_id"),
                SandboxRequestStore.instant(rs, "received_at"),
                evidence ? rs.getString("event_json") : null);
    }
}
