package io.github.leontecristian.workflow.testhost.service.event;

import io.github.leontecristian.workflow.core.dto.runtime.WorkflowEvent;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowEventRecorder;
import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings;
import io.github.leontecristian.workflow.testhost.repository.event.ConsoleEventStore;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public class ConsoleEventRecorder implements WorkflowEventRecorder {
    private final ConsoleEventStore events;
    private final ConsoleWire wire;
    private final ConsoleSettings settings;

    public ConsoleEventRecorder(
            ConsoleEventStore events, ConsoleWire wire, ConsoleSettings settings) {
        this.events = events;
        this.wire = wire;
        this.settings = settings;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(List<WorkflowEvent> batch) {
        for (var event : batch) {
            String json = wire.json(event);
            if (!wire.fits(json, 128 * 1024))
                throw new IllegalStateException("Event evidence exceeds bound");
            events.record(event, json);
        }
        if (settings.failEventRecording)
            throw new IllegalStateException("Configured event recording failure");
    }
}
