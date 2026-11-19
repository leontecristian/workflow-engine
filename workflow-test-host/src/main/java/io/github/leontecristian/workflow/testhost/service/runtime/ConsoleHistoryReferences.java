package io.github.leontecristian.workflow.testhost.service.runtime;

import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** A bounded, process-local lookup for sandbox history links. */
final class ConsoleHistoryReferences {
    enum Kind { EVENT, PAGE }

    private record Position(Kind kind, String actor, long instanceId, long sequence) {}

    private final int capacity;
    private final Map<Position, String> references = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, Position> positions = new HashMap<>();

    ConsoleHistoryReferences(int capacity) {
        this.capacity = capacity;
    }

    synchronized String issue(Kind kind, String actor, long instanceId, long sequence) {
        var position = new Position(kind, actor, instanceId, sequence);
        String existing = references.get(position);
        if (existing != null) return existing;

        if (references.size() == capacity) {
            var oldest = references.entrySet().iterator();
            positions.remove(oldest.next().getValue());
            oldest.remove();
        }

        String reference;
        do {
            reference = "h-" + UUID.randomUUID();
        } while (positions.containsKey(reference));
        references.put(position, reference);
        positions.put(reference, position);
        return reference;
    }

    synchronized long resolve(Kind kind, String actor, long instanceId, String reference) {
        var position = positions.get(reference);
        if (position == null || position.kind() != kind || position.instanceId() != instanceId
                || !position.actor().equals(actor)) throw ConsoleException.invalid();
        references.get(position); // Only a matching lookup refreshes retention.
        return position.sequence();
    }
}
