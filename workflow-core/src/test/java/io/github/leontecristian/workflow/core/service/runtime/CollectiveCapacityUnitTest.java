package io.github.leontecristian.workflow.core.service.runtime;

import static org.junit.jupiter.api.Assertions.*;

import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import org.junit.jupiter.api.Test;

class CollectiveCapacityUnitTest {
    @Test void executionValueAcceptsEightMiBAndRejectsOneExtraCodeUnit() {
        String value = array(WorkflowJsonValue.MAX_UTF16_BYTES / 2, "x");
        assertEquals(WorkflowJsonValue.MAX_UTF16_BYTES / 2, value.length());
        assertEquals(value, WorkflowJsonValue.parse(value).toJson());
        var failure = assertThrows(WorkflowExecutionException.class,
                () -> WorkflowJsonValue.parse(value.substring(0, value.length()-2) + "x\"]"));
        assertEquals(Category.LIMIT_EXCEEDED, failure.category());
    }

    @Test void unicodeAndEscapesCountCanonicalBytesAndPreserveExactDecimals() {
        assertEquals("{\"amount\":1000.00,\"text\":\"界\\\"\"}",
                WorkflowJsonValue.parse("{\"text\":\"界\\\"\",\"amount\":1000.00}").toJson());
        String value = array(WorkflowJsonValue.MAX_UTF16_BYTES / 2, "界");
        assertTrue(value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= WorkflowJsonValue.MAX_UTF8_BYTES);
        assertEquals(value, WorkflowJsonValue.parse(value).toJson());
    }

    @Test void genericDepthEighteenDoesNotRelaxResultDepthSixteen() {
        assertDoesNotThrow(() -> WorkflowJsonValue.parse("[".repeat(18)+"]".repeat(18)));
        assertThrows(WorkflowExecutionException.class, () -> WorkflowJsonValue.parse("[".repeat(19)+"]".repeat(19)));
        assertDoesNotThrow(() -> CompleteWorkflowTask.parseResult("{\"v\":"+"[".repeat(15)+"]".repeat(15)+"}"));
        assertThrows(WorkflowExecutionException.class, () -> CompleteWorkflowTask.parseResult("{\"v\":"+"[".repeat(16)+"]".repeat(16)+"}"));
        String result = "{\"a\":\""+"x".repeat(16000)+"\",\"b\":\""+"x".repeat(16000)+"\",\"c\":\""+"x".repeat(746)+"\"}";
        assertEquals(32768, result.length());
        assertDoesNotThrow(() -> CompleteWorkflowTask.parseResult(result));
        assertThrows(WorkflowExecutionException.class, () -> CompleteWorkflowTask.parseResult(result.replace("\"c\":\"", "\"c\":\"x")));
    }

    @Test void completeMapAndHistoryUseTheirOwnInclusiveBudgets() {
        assertDoesNotThrow(() -> RuntimeValueBindings.envelope("x".repeat(RuntimeJson.MAP_UTF16_BYTES / 2)));
        assertThrows(WorkflowExecutionException.class,
                () -> RuntimeValueBindings.envelope("x".repeat(RuntimeJson.MAP_UTF16_BYTES / 2 + 1)));
        RuntimeJson json = new RuntimeJson();
        var node = json.object().put("text", "x".repeat(RuntimeJson.HISTORY_UTF16_BYTES / 2 - 11));
        String details = json.history(node);
        assertEquals(RuntimeJson.HISTORY_UTF16_BYTES / 2, details.length());
        assertEquals(node, json.tree(details));
        node.put("text", node.get("text").textValue()+"x");
        assertThrows(WorkflowExecutionException.class, () -> json.history(node));
    }

    @Test void commandHistoryAcceptsSixtyFourMiBAndRejectsNextRecord() {
        var json = org.mockito.Mockito.mock(RuntimeJson.class);
        var entityManager = org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class);
        String twentyFourMiB = "x".repeat(RuntimeJson.HISTORY_UTF16_BYTES / 2);
        String sixteenMiB = "x".repeat(8 * 1024 * 1024);
        org.mockito.Mockito.when(json.history(org.mockito.ArgumentMatchers.any()))
                .thenReturn(twentyFourMiB, twentyFourMiB, sixteenMiB, "{}");
        org.mockito.Mockito.when(json.encode(org.mockito.ArgumentMatchers.any())).thenReturn("{}");
        var context = new io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext(
                io.github.leontecristian.workflow.core.dto.runtime.ActorKind.USER, "capacity",
                java.time.Instant.EPOCH, "capacity");
        var batch = new RuntimeHistoryBatch(entityManager, json, 1, 2, java.util.UUID.randomUUID(), context);
        for (int i=0; i<3; i++) batch.next("VARIABLES_WRITTEN", null, null, null, java.util.Map.of(), (short) 2);
        assertEquals(3, batch.last());
        assertThrows(WorkflowExecutionException.class,
                () -> batch.next("VARIABLES_WRITTEN", null, null, null, java.util.Map.of(), (short) 2));
        assertEquals(3, batch.last());
    }

    private static String array(int chars, String character) {
        StringBuilder result = new StringBuilder("[");
        while (chars - result.length() - 1 > 16002) {
            if (result.length() > 1) result.append(',');
            result.append('"').append(character.repeat(15999)).append('"');
        }
        if (result.length() > 1) result.append(',');
        result.append('"').append(character.repeat(chars-result.length()-2)).append('"').append(']');
        return result.toString();
    }
}
