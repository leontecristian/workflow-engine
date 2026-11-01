package io.github.leontecristian.workflow.core.service.runtime;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.NullLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.StartInputs;
import io.github.leontecristian.workflow.core.dto.runtime.StartWorkflow;
import io.github.leontecristian.workflow.core.dto.runtime.TextLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowOperation;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowCommandReceiptEntity;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuntimeReceiptCompatibilityUnitTest {
    private final RuntimeJson json=new RuntimeJson();
    private final UUID id=UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private WorkflowCommandContext context(String actor) { return new WorkflowCommandContext(ActorKind.USER,actor,Instant.parse("2026-01-01T00:00:00Z"),"test"); }
    private String resource(String name) throws Exception { return new String(getClass().getResourceAsStream("/f007/"+name).readAllBytes(),StandardCharsets.UTF_8); }
    @Test void independentlySpecifiedFingerprintBytesAndHashesRemainStable() {
        var command=new StartWorkflow(id,42,null,null,null); var context=context("f007.admin");
        assertEquals("1214c3e3b82d7d6af165aba1e867d06fc9ea3697b3dbcec5e61f52d57c1e69ef",HexFormat.of().formatHex(json.fingerprint(command,context)));
        assertArrayEquals(json.fingerprint(command,context),json.fingerprint(command,StartInputs.empty(),context));
        var inputs=new StartInputs(Map.of("quantity",new TextLiteral("9007199254740993")));
        assertEquals("929d0614b1368316890117ff578fc63c07c534ffa6c7110a4d2de26f811d3076",HexFormat.of().formatHex(json.fingerprint(command,inputs,context)));
        for (var pair:java.util.List.of(java.util.List.of(new TextLiteral("12.5"),new TextLiteral("12.50")),
                java.util.List.of(new TextLiteral("2026-01-01T00:00:00Z"),new TextLiteral("2026-01-01T02:00:00+02:00"))))
            assertFalse(java.util.Arrays.equals(json.fingerprint(command,new StartInputs(Map.of("v",pair.get(0))),context),json.fingerprint(command,new StartInputs(Map.of("v",pair.get(1))),context)));
        assertFalse(java.util.Arrays.equals(json.fingerprint(command,context),json.fingerprint(command,new StartInputs(Map.of("v",NullLiteral.INSTANCE)),context)));
    }
    @Test void immutableLegacyAndExtendedSnapshotsReplayWithoutCurrentState() throws Exception {
        for (boolean extended:new boolean[]{false,true}) for (boolean complete:new boolean[]{false,true}) {
            String name=(extended?"extended":"legacy")+(complete?"-complete":"-start");
            String bytes=resource(name+".json");
            assertEquals(resource(name+".sha256").trim(),HexFormat.of().formatHex(RuntimeJson.hash(bytes)));
            var op=complete?WorkflowOperation.COMPLETE_TASK:WorkflowOperation.START_WORKFLOW;
            var ctx=context(complete?"reviewer":"f007.admin");
            var receipt=new WorkflowCommandReceiptEntity(id,1,op.name(),ActorKind.USER,ctx.actorRef(),new byte[32],RuntimeJson.FINGERPRINT_VERSION,
                    LocalDateTime.parse(complete?"2026-01-02T00:00:00":"2026-01-01T00:00:00"),bytes,(short)(extended?2:1));
            var result=json.replay(receipt,id,op,ctx,new byte[32]).orElseThrow();
            assertEquals(bytes,json.evidence(result));
            assertEquals(extended?8:3,result.activity().activationSequence());
            assertEquals(Category.COMMAND_ID_CONFLICT,assertThrows(WorkflowExecutionException.class,() -> json.replay(receipt,id,op,context("other"),new byte[32])).category());
            if (extended) {
                var invalid=new WorkflowCommandReceiptEntity(id,1,op.name(),ActorKind.USER,ctx.actorRef(),new byte[32],RuntimeJson.FINGERPRINT_VERSION,receipt.getRecordedAt(),bytes,(short)1);
                assertThrows(WorkflowExecutionException.class,() -> json.replay(invalid,id,op,ctx,new byte[32]));
            }
        }
    }
}
