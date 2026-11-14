package io.github.leontecristian.workflow.testhost;

import io.github.leontecristian.workflow.testhost.controller.console.ConsoleSessionController;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MVC dispatch only: no application startup, database or external services. */
class ConsolePageRoutingUnitTest {
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ConsoleSessionController(null, null, null)).build();

    @Test
    void directPageRequestsAndRefreshesForwardToAngularWithoutChangingTheUrl() throws Exception {
        var mapping = ConsoleSessionController.class.getMethod("consoleIndex").getAnnotation(GetMapping.class);
        for (String path : mapping.value()) {
            mvc.perform(get(path.replaceAll("\\{[^}]+}", "123")).queryParam("filter", "active"))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/console/index.html"));
        }
        mvc.perform(get("/console")).andExpect(status().isFound())
                .andExpect(redirectedUrl("/console/"));
    }

    @Test
    void missingApiAssetsAndUnknownPathsAreNotPageForwards() throws Exception {
        for (String path : new String[] {"/console/api/missing", "/console/missing.js",
                "/console/assets/missing.svg", "/console/unknown", "/console/logout"}) {
            mvc.perform(get(path)).andExpect(status().isNotFound());
        }
        mvc.perform(post("/console/my-tasks")).andExpect(status().isMethodNotAllowed());
    }
}
