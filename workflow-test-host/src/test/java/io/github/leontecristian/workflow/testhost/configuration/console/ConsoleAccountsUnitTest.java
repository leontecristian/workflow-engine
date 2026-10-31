package io.github.leontecristian.workflow.testhost.configuration.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class ConsoleAccountsUnitTest {
    @Test
    void repeatedLoginsDoNotEraseStoredAccountCredentialsAndReferencesRemainExact() {
        var env =
                new MockEnvironment()
                        .withProperty("workflow.console.enabled", "true")
                        .withProperty("workflow.console.admin.username", "admin")
                        .withProperty("workflow.console.admin.password", "unit-only-password")
                        .withProperty("workflow.console.reviewer.username", "reviewer ")
                        .withProperty("workflow.console.reviewer.password", "unit-only-password");
        var users = new ConsoleSecurity().consoleUsers(new ConsoleSettings(env));
        var provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(users);
        provider.setPasswordEncoder(new BCryptPasswordEncoder());
        var manager = new ProviderManager(provider);
        for (int attempt = 0; attempt < 2; attempt++)
            assertEquals(
                    "reviewer ",
                    manager.authenticate(
                                    UsernamePasswordAuthenticationToken.unauthenticated(
                                            "reviewer ", "unit-only-password"))
                            .getName());
        assertThrows(
                org.springframework.security.core.AuthenticationException.class,
                () ->
                        manager.authenticate(
                                UsernamePasswordAuthenticationToken.unauthenticated(
                                        "reviewer", "unit-only-password")));
    }

    @Test
    void enabledConsoleRequiresExternalCredentials() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        new ConsoleSettings(
                                new MockEnvironment()
                                        .withProperty("workflow.console.enabled", "true")));
    }
}
