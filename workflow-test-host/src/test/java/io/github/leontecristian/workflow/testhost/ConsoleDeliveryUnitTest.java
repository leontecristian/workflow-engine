package io.github.leontecristian.workflow.testhost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.leontecristian.workflow.testhost.dto.event.InboxView;
import io.github.leontecristian.workflow.testhost.dto.event.OutboxView;
import io.github.leontecristian.workflow.testhost.repository.event.ConsoleEventStore;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import io.github.leontecristian.workflow.testhost.service.event.ConsoleEventDelivery;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

class ConsoleDeliveryUnitTest {
    @Test
    void exactDuplicateDoesNotInsertAnotherEffectButRecordsCommittedAttempt() {
        var store = mock(ConsoleEventStore.class);
        var context = mock(ConsoleContext.class);
        when(context.now()).thenReturn(Instant.EPOCH);
        var event =
                new OutboxView(
                        1,
                        "e",
                        1,
                        1,
                        UUID.randomUUID(),
                        "TASK_CREATED",
                        Instant.EPOCH,
                        Instant.EPOCH,
                        1,
                        "{\"a\":1}");
        when(store.find("e", true)).thenReturn(event);
        when(store.findInbox("e"))
                .thenReturn(new InboxView(1, "e", Instant.EPOCH, event.eventJson()));
        assertEquals(
                true, new ConsoleEventDelivery(store, context).redeliver("e").get("duplicate"));
        verify(store, never()).insertInbox(any(), any(), any());
        verify(store).delivered(event, Instant.EPOCH);
    }

    @Test
    void collationEquivalentPayloadIsNotAnExactDuplicate() {
        var store = mock(ConsoleEventStore.class);
        var event =
                new OutboxView(
                        1,
                        "e",
                        1,
                        1,
                        UUID.randomUUID(),
                        "TASK_CREATED",
                        Instant.EPOCH,
                        null,
                        0,
                        "{\"a\":\"A\"}");
        when(store.find("e", true)).thenReturn(event);
        when(store.findInbox("e"))
                .thenReturn(new InboxView(1, "e", Instant.EPOCH, "{\"a\":\"a\"}"));
        assertThrows(
                IllegalStateException.class,
                () -> new ConsoleEventDelivery(store, mock(ConsoleContext.class)).redeliver("e"));
        verify(store, never()).delivered(any(), any());
    }
}
