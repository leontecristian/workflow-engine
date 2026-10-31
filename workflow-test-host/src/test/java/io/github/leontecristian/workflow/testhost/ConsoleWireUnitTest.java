package io.github.leontecristian.workflow.testhost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;

class ConsoleWireUnitTest {
    private final ConsoleWire wire = new ConsoleWire();

    @Test
    void evidenceMapsAreDeterministicAndMalformedUnicodeIsRejected() {
        var first = new java.util.LinkedHashMap<String, Object>();
        first.put("z", 1L);
        first.put("a", 2L);
        var second = new java.util.LinkedHashMap<String, Object>();
        second.put("a", 2L);
        second.put("z", 1L);
        assertEquals(wire.json(first), wire.json(second));
        var value = wire.tree("{\"name\":\"\\ud800\"}");
        assertThrows(ConsoleException.class, () -> ConsoleWire.text(value, "name", false));
    }

    @Test
    void trustedClockIsTruncatedToSqlServerPrecision() {
        var time = java.time.Instant.parse("2026-10-31T12:00:00.123456789Z");
        var context =
                new io.github.leontecristian.workflow.testhost.service.console.ConsoleContext(
                        java.time.Clock.fixed(time, java.time.ZoneOffset.UTC));
        assertEquals(java.time.Instant.parse("2026-10-31T12:00:00.123456700Z"), context.now());
    }

    record Tokens(long id, Long nextAfterId, int version) {}

    @Test
    void longTokensStayExactAndVersionsRemainNumbers() {
        assertEquals(
                "{\"id\":\"9007199254740993\",\"nextAfterId\":null,\"version\":3}",
                wire.json(new Tokens(9007199254740993L, null, 3)));
        assertEquals(Long.MAX_VALUE, ConsoleWire.decimal("9223372036854775807", false));
        for (String invalid : new String[] {"01", "1e3", "1.0", "-1", "9223372036854775808"})
            assertThrows(ConsoleException.class, () -> ConsoleWire.decimal(invalid, false));
    }

    @Test
    void duplicateEscapedUnknownAndTrailingFieldsAreRejected() {
        for (String body :
                new String[] {
                    "{\"name\":\"x\",\"na\\u006de\":\"y\"}",
                    "{\"name\":\"x\",\"actor\":\"admin\"}",
                    "{\"name\":\"x\"} {}"
                }) {
            var request = request(body);
            assertThrows(ConsoleException.class, () -> wire.body(request, "name"));
        }
    }

    @Test
    void streamedLimitsAndStrictUtf8DoNotRelyOnContentLength() {
        var request = request("x".repeat(16385));
        assertEquals(413, assertThrows(ConsoleException.class, () -> wire.body(request)).status());
        var malformed = request("");
        malformed.setContent(new byte[] {(byte) 0xc3, 0x28});
        assertThrows(ConsoleException.class, () -> wire.raw(malformed, 100));
    }

    @Test
    void rawDocumentPreservesLargeNumbersAndWhitespace() {
        String document = " {\"designer\":{\"id\":900719925474099312345}}\n";
        assertEquals(document, wire.raw(request(document), 4096));
    }

    @Test
    void byteBoundedPagesKeepAnExactContinuationWithoutSkippingItems() {
        record Row(long id, String value) {}
        var rows =
                java.util.List.of(new Row(1, "x".repeat(300000)), new Row(2, "x".repeat(300000)));
        var page = wire.page(rows, null, "nextAfterId", Row::id);
        assertEquals(1L, page.get("nextAfterId"));
        assertEquals(1, ((java.util.List<?>) page.get("items")).size());
    }

    private MockHttpServletRequest request(String text) {
        var r = new MockHttpServletRequest();
        r.setContentType("application/json");
        r.setContent(text.getBytes(StandardCharsets.UTF_8));
        return r;
    }
}
