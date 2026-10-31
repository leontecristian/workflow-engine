package io.github.leontecristian.workflow.testhost.controller.console;

import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.util.Map;

@RestController
@ConditionalOnProperty(name = "workflow.console.enabled", havingValue = "true")
public class ConsoleSessionController {
    @GetMapping("/console")
    public org.springframework.web.servlet.ModelAndView consoleRedirect() {
        return new org.springframework.web.servlet.ModelAndView("redirect:/console/");
    }

    @GetMapping("/console/")
    public org.springframework.web.servlet.ModelAndView consoleIndex() {
        return new org.springframework.web.servlet.ModelAndView("forward:/console/index.html");
    }

    private final ConsoleWire wire;
    private final ConsoleContext context;
    private final ConsoleSettings settings;

    public ConsoleSessionController(
            ConsoleWire wire, ConsoleContext context, ConsoleSettings settings) {
        this.wire = wire;
        this.context = context;
        this.settings = settings;
    }

    @GetMapping(value = "/console/login", produces = "text/html")
    public String login(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return "<!doctype html><html><head><title>Workflow console"
                + " login</title></head><body><h1>Local workflow console</h1>"
                + (request.getParameter("error") != null ? "<p role=alert>Sign in failed.</p>" : "")
                + "<form method=post"
                + " action=/console/login><label>Username <input name=username"
                + " autocomplete=username required></label><label>Password <input type=password"
                + " name=password autocomplete=current-password required></label><input"
                + " type=hidden name=_csrf value=\""
                + HtmlUtils.htmlEscape(token.getToken())
                + "\"><button>Sign in</button></form></body></html>";
    }

    @GetMapping("/console/api/session")
    public ResponseEntity<String> session(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return wire.response(
                Map.of(
                        "actorRef",
                        context.actor(),
                        "roles",
                        context.admin()
                                ? new String[] {"CONSOLE_ADMIN"}
                                : new String[] {"CONSOLE_REVIEWER"},
                        "csrfToken",
                        token.getToken(),
                        "csrfHeaderName",
                        token.getHeaderName()));
    }

    @GetMapping("/console/api/capabilities")
    @PreAuthorize("hasRole('CONSOLE_ADMIN')")
    public ResponseEntity<String> capabilities() {
        return wire.response(
                Map.of(
                        "mode",
                        settings.sample ? "sample" : "none",
                        "reviewer",
                        settings.reviewer,
                        "singleAssignments",
                        settings.sample ? new String[] {"sandbox.singleReviewer"} : new String[] {},
                        "taskTypes",
                        settings.sample
                                ? Map.of("SANDBOX_REVIEW", new String[] {"COMPLETE"})
                                : Map.of(),
                        "failureFlags",
                        Map.of(
                                "failAudit",
                                settings.failAudit,
                                "failStartLink",
                                settings.failStartLink,
                                "failEventRecording",
                                settings.failEventRecording,
                                "failRuntimeAfterResult",
                                settings.failRuntimeAfterResult)));
    }
}
