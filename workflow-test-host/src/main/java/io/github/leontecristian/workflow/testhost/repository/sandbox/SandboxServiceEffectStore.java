package io.github.leontecristian.workflow.testhost.repository.sandbox;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionContext;
import io.github.leontecristian.workflow.testhost.dto.sandbox.SandboxServiceEffect;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SandboxServiceEffectStore {
    private final JdbcTemplate jdbc;
    public SandboxServiceEffectStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Transactional(propagation=Propagation.MANDATORY)
    public void insert(ServiceActionContext context,long quantity) {
        var ids=jdbc.queryForList("SELECT id FROM wf_test.sandbox_request WHERE instance_id=?",Long.class,context.instanceId());
        if (ids.size()!=1 || !"SANDBOX_REQUEST".equals(context.businessType()) || !Long.toString(ids.get(0)).equals(context.businessId()))
            throw new IllegalStateException("Missing or mismatched sandbox link");
        jdbc.update("INSERT INTO wf_test.service_effect(request_id,instance_id,command_id,node_key,quantity,actor_ref,occurred_at) VALUES(?,?,?,?,?,?,?)",
                ids.get(0),context.instanceId(),context.commandId().toString(),context.nodeKey(),quantity,context.commandContext().actorRef(),SandboxRequestStore.utc(context.commandContext().occurredAt()));
    }
    @Transactional(readOnly=true)
    public List<SandboxServiceEffect> page(long instanceId,long after,int count) {
        return jdbc.query("SELECT TOP (?) * FROM wf_test.service_effect WHERE instance_id=? AND id>? ORDER BY id",
                (rs,row) -> new SandboxServiceEffect(rs.getLong("id"),rs.getLong("request_id"),rs.getLong("instance_id"),
                        java.util.UUID.fromString(rs.getString("command_id")),rs.getString("node_key"),rs.getLong("quantity"),rs.getString("actor_ref"),SandboxRequestStore.instant(rs,"occurred_at")),
                count,instanceId,after);
    }
}
