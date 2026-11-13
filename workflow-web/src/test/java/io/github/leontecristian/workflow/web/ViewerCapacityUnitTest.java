package io.github.leontecristian.workflow.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.github.leontecristian.workflow.core.service.runtime.RuntimeJson;
import io.github.leontecristian.workflow.web.service.instance.ViewerWire;
import org.junit.jupiter.api.Test;

class ViewerCapacityUnitTest {
    @Test void maximumDecodedHistoryStringSurvivesEscapedProtocolEnvelope() {
        var wire = new ViewerWire();
        String details = "\"".repeat(RuntimeJson.HISTORY_UTF16_BYTES / 2);
        String envelope = wire.json(java.util.Map.of("detailsJson", details));
        assertTrue(envelope.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < RuntimeJson.HISTORY_RESPONSE_UTF8_BYTES);
        assertEquals(details, wire.tree(envelope).get("detailsJson").textValue());
    }
}
