package io.github.leontecristian.workflow.testhost.service.sandbox;

import io.github.leontecristian.workflow.testhost.dto.sandbox.SandboxRequestView;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsoleSandboxService {
    private final SandboxRequestStore requests;
    private final ConsoleContext context;

    public ConsoleSandboxService(SandboxRequestStore requests, ConsoleContext context) {
        this.requests = requests;
        this.context = context;
    }

    @Transactional
    public SandboxRequestView create(String name, String requestDataJson) {
        return requests.create(name, requestDataJson, context.actor(), context.now());
    }
}
