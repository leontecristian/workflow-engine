package io.github.leontecristian.workflow.testhost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.github.leontecristian.workflow.core.service.runtime.RuntimeJson;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;
import org.junit.jupiter.api.Test;

class ConsoleCapacityUnitTest {
    @Test void maximumDecodedHistoryStringSurvivesEscapedProtocolEnvelope() {
        var wire = new ConsoleWire();
        String details = "\"".repeat(RuntimeJson.HISTORY_UTF16_BYTES / 2);
        String envelope = wire.json(java.util.Map.of("detailsJson", details));
        assertTrue(envelope.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < RuntimeJson.HISTORY_RESPONSE_UTF8_BYTES);
        assertEquals(details, wire.tree(envelope).get("detailsJson").textValue());
    }
}
