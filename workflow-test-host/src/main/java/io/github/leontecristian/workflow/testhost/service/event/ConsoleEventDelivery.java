package io.github.leontecristian.workflow.testhost.service.event;

import io.github.leontecristian.workflow.testhost.dto.event.OutboxView;
import io.github.leontecristian.workflow.testhost.repository.event.ConsoleEventStore;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class ConsoleEventDelivery {
    private final ConsoleEventStore events;
    private final ConsoleContext context;

    public ConsoleEventDelivery(ConsoleEventStore events, ConsoleContext context) {
        this.events = events;
        this.context = context;
    }

    @Transactional
    public Map<String, Integer> dispatch(int limit) {
        int delivered = 0, duplicates = 0;
        for (var event : events.pending(limit)) {
            if (deliver(event)) duplicates++;
            else delivered++;
        }
        return Map.of("delivered", delivered, "duplicates", duplicates);
    }

    @Transactional
    public Map<String, Object> redeliver(String id) {
        var event = events.find(id, true);
        return Map.of("eventId", event.eventId(), "duplicate", deliver(event));
    }

    private boolean deliver(OutboxView event) {
        var existing = events.findInbox(event.eventId());
        boolean duplicate = existing != null;
        if (duplicate && !existing.eventJson().equals(event.eventJson()))
            throw new IllegalStateException("Inbox event evidence mismatch");
        var time = context.now();
        if (!duplicate) events.insertInbox(event.eventId(), time, event.eventJson());
        events.delivered(event, time);
        return duplicate;
    }
}
