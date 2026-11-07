package io.github.leontecristian.workflow.testhost;

import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleErrors;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.assertThat;

/** Error translation and logging only; no host, persistence or external service. */
@ExtendWith(OutputCaptureExtension.class)
class ConsoleErrorsUnitTest {
    @Test void serverFailuresLogOriginalExceptionsWhileResponsesStaySafe(CapturedOutput output) {
        var errors = new ConsoleErrors(new ConsoleWire());
        var request = new MockHttpServletRequest();
        request.setAttribute("console.correlation", "console-log-id");
        var unexpected = new IllegalStateException("private-console-detail", new IllegalArgumentException("private-console-cause"));
        var configuration = new WorkflowExecutionException(WorkflowExecutionException.Category.HOST_CONFIGURATION,
                new IllegalArgumentException("private-console-configuration-cause"));
        for (RuntimeException failure : List.of(unexpected, configuration)) {
            var response = errors.error(failure, request);
            String code = failure == unexpected ? "HOST_FAILURE" : "HOST_CONFIGURATION";
            assertThat(response.getStatusCode().value()).isEqualTo(500);
            assertThat(response.getBody()).isEqualTo("{\"code\":\"" + code + "\",\"message\":\"" + code
                    + "\",\"diagnostics\":[],\"correlationId\":\"console-log-id\"}");
        }
        request.setAttribute("console.correlation", "expected-console-404");
        assertThat(errors.error(ConsoleException.missing(), request).getStatusCode().value()).isEqualTo(404);
        assertThat(output.getAll()).contains("Console failure console-log-id (HOST_FAILURE)",
                "Console failure console-log-id (HOST_CONFIGURATION)",
                "java.lang.IllegalStateException: private-console-detail",
                "Caused by: java.lang.IllegalArgumentException: private-console-cause",
                "Caused by: java.lang.IllegalArgumentException: private-console-configuration-cause",
                "at " + ConsoleErrorsUnitTest.class.getName())
                .doesNotContain("Console failure expected-console-404");
    }
}
