package io.github.leontecristian.workflow.testhost;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.NullLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.ScalarValue;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionContext;
import io.github.leontecristian.workflow.core.dto.runtime.TextLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings;
import io.github.leontecristian.workflow.testhost.controller.runtime.ConsoleRuntimeController;
import io.github.leontecristian.workflow.testhost.dto.runtime.SandboxStart;
import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxServiceEffectStore;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeCoordinator;
import io.github.leontecristian.workflow.testhost.service.sandbox.SandboxRecordQuantityAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

class ConsoleScalarServiceUnitTest {
    @Test void jdbcEffectUsesVerifiedHostLinkExactLongAndExistingUuidStringConvention() {
        var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(jdbc.queryForList(anyString(),eq(Long.class),eq(1L))).thenReturn(java.util.List.of(2L));
        var store=new SandboxServiceEffectStore(jdbc);
        var id=UUID.randomUUID();
        var context=new WorkflowCommandContext(ActorKind.USER,"reviewer",Instant.EPOCH,"test");
        store.insert(new ServiceActionContext(id,1,42,"AFTER","SANDBOX_REQUEST","2",context),9007199254740993L);
        verify(jdbc).update(anyString(),eq(2L),eq(1L),eq(id.toString()),eq("AFTER"),eq(9007199254740993L),eq("reviewer"),eq(java.time.LocalDateTime.of(1970,1,1,0,0)));
        assertThrows(IllegalStateException.class,() -> store.insert(new ServiceActionContext(id,1,42,"AFTER","SANDBOX_REQUEST","3",context),1));
        verify(jdbc,times(1)).update(anyString(),any(),any(),any(),any(),any(),any(),any());
    }
    private MockEnvironment environment() {
        return new MockEnvironment().withProperty("workflow.console.enabled","true").withProperty("workflow.console.admin.username","admin")
                .withProperty("workflow.console.reviewer.username","reviewer").withProperty("workflow.console.admin.password","test").withProperty("workflow.console.reviewer.password","test");
    }
    @Test void failureSettingsAreStartupOnlyValidatedAndSampleWritesBeforeFailure() {
        assertThrows(IllegalStateException.class,() -> new ConsoleSettings(new MockEnvironment().withProperty("workflow.console.fail-service-node","AFTER")));
        assertThrows(IllegalStateException.class,() -> new ConsoleSettings(environment().withProperty("workflow.console.fail-service-node","bad key")));
        assertThrows(IllegalStateException.class,() -> new ConsoleSettings(environment().withProperty("workflow.console.fail-service-node","AFTER").withProperty("workflow.console.invalid-service-output-node","AFTER")));
        var effects=mock(SandboxServiceEffectStore.class);
        var ctx=new ServiceActionContext(UUID.randomUUID(),1,42,"AFTER","SANDBOX_REQUEST","2",new WorkflowCommandContext(ActorKind.USER,"reviewer",Instant.EPOCH,"test"));
        var inputs=Map.of("quantity",new ScalarValue(VariableValueType.LONG,new TextLiteral("9007199254740993")));
        var normal=new SandboxRecordQuantityAction(effects,new ConsoleSettings(environment()));
        assertEquals(inputs.get("quantity"),normal.execute(ctx,inputs).outputs().get("recordedQuantity"));
        var fail=new SandboxRecordQuantityAction(effects,new ConsoleSettings(environment().withProperty("workflow.console.fail-service-node","AFTER")));
        assertThrows(IllegalStateException.class,() -> fail.execute(ctx,inputs));
        var invalid=new SandboxRecordQuantityAction(effects,new ConsoleSettings(environment().withProperty("workflow.console.invalid-service-output-node","AFTER")));
        assertTrue(invalid.execute(ctx,inputs).outputs().isEmpty()); verify(effects,times(3)).insert(ctx,9007199254740993L);
    }
    @Test void startTransportKeepsTokensAndRejectsNumbersContainersNullDuplicateAndUnknownFields() {
        var commands=mock(ConsoleRuntimeCoordinator.class); var context=mock(ConsoleContext.class);
        var controller=new ConsoleRuntimeController(null,null,null,commands,null,context,new ConsoleWire(),null);
        String prefix="{\"commandId\":\"12345678-1234-1234-1234-123456789abc\",\"definitionVersionId\":\"42\",\"requestId\":\"1\",\"expectedRequestVersion\":\"0\"";
        controller.start(request(prefix+",\"inputs\":{\"quantity\":\"9007199254740993\",\"flag\":true,\"note\":null}}"));
        var captured=ArgumentCaptor.forClass(SandboxStart.class); verify(commands).start(captured.capture(),any());
        assertEquals(new TextLiteral("9007199254740993"),captured.getValue().inputs().values().get("quantity"));
        assertEquals(NullLiteral.INSTANCE,captured.getValue().inputs().values().get("note"));
        for(String suffix:java.util.List.of(",\"inputs\":null}",",\"inputs\":{\"q\":1}}",",\"inputs\":{\"q\":{}}}",",\"inputs\":{\"q\":\"1\",\"q\":\"2\"}}",",\"actor\":\"untrusted\"}"))
            assertThrows(ConsoleException.class,() -> controller.start(request(prefix+suffix)));
        String tooMany=java.util.stream.IntStream.range(0,101).mapToObj(i -> "\"key"+i+"\":null").collect(java.util.stream.Collectors.joining(","));
        assertEquals(422,assertThrows(ConsoleException.class,() -> controller.start(request(prefix+",\"inputs\":{"+tooMany+"}}"))).status());
        var wire=new ConsoleWire();
        assertDoesNotThrow(() -> wire.body(request("{\"inputs\":{\"large\":\""+"x".repeat(17000)+"\"}}"),256*1024,"inputs"));
        assertEquals(413,assertThrows(ConsoleException.class,() -> wire.body(request("{\"inputs\":\""+"x".repeat(256*1024)+"\"}"),256*1024,"inputs")).status());
        assertEquals(413,assertThrows(ConsoleException.class,() -> wire.body(request("{\"name\":\""+"x".repeat(17000)+"\"}"),"name")).status());
    }
    private MockHttpServletRequest request(String text) {
        var request=new MockHttpServletRequest(); request.setContentType("application/json"); request.setContent(text.getBytes(StandardCharsets.UTF_8)); return request;
    }
}
