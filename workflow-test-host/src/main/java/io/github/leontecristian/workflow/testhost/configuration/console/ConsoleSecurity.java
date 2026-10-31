package io.github.leontecristian.workflow.testhost.configuration.console;

import io.github.leontecristian.workflow.testhost.service.console.ConsoleErrors;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Configuration
@EnableMethodSecurity
public class ConsoleSecurity {
    @Bean
    java.time.Clock consoleClock() {
        return java.time.Clock.systemUTC();
    }

    @Bean
    WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> consoleLoopback(
            ConsoleSettings settings, Environment env) {
        return factory -> {
            if (!settings.enabled) return;
            try {
                InetAddress address =
                        InetAddress.getByName(env.getProperty("server.address", "127.0.0.1"));
                if (!address.isLoopbackAddress())
                    throw new IllegalStateException("Console must bind to loopback");
                factory.setAddress(address);
            } catch (java.net.UnknownHostException e) {
                throw new IllegalStateException("Invalid console bind address", e);
            }
        };
    }

    @Bean
    UserDetailsService consoleUsers(ConsoleSettings settings) {
        Map<String, UserDetails> users = new HashMap<>();
        var encoder = new BCryptPasswordEncoder();
        if (settings.enabled) {
            users.put(
                    settings.admin,
                    User.withUsername(settings.admin)
                            .password(encoder.encode(settings.adminPassword))
                            .roles("CONSOLE_ADMIN")
                            .build());
            users.put(
                    settings.reviewer,
                    User.withUsername(settings.reviewer)
                            .password(encoder.encode(settings.reviewerPassword))
                            .roles("CONSOLE_REVIEWER")
                            .build());
        }
        return username -> {
            UserDetails user = users.get(username);
            if (user == null) throw new UsernameNotFoundException("Unknown account");
            // ProviderManager erases credentials on the authenticated User; never erase the stored
            // template.
            return User.withUserDetails(user).build();
        };
    }

    @Bean
    SecurityFilterChain consoleFilterChain(
            HttpSecurity http,
            ConsoleSettings settings,
            UserDetailsService users,
            ConsoleErrors errors)
            throws Exception {
        http.addFilterBefore(
                new OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(
                            HttpServletRequest request,
                            HttpServletResponse response,
                            FilterChain chain)
                            throws ServletException, IOException {
                        request.setAttribute("console.correlation", UUID.randomUUID().toString());
                        if (!settings.enabled
                                && (request.getRequestURI().equals("/console")
                                        || request.getRequestURI().startsWith("/console/"))) {
                            errors.write(request, response, 404, "NOT_FOUND");
                            return;
                        }
                        chain.doFilter(request, response);
                    }
                },
                CsrfFilter.class);
        var csrf = new HttpSessionCsrfTokenRepository();
        http.csrf(
                config ->
                        config.csrfTokenRepository(csrf)
                                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()));
        http.authorizeHttpRequests(
                auth ->
                        auth.requestMatchers("/console/login", "/error")
                                .permitAll()
                                .requestMatchers("/console", "/console/**")
                                .hasAnyRole("CONSOLE_ADMIN", "CONSOLE_REVIEWER")
                                .anyRequest()
                                .permitAll());
        http.exceptionHandling(
                config ->
                        config.authenticationEntryPoint(
                                        (request, response, failure) -> {
                                            if (request.getRequestURI().startsWith("/console/api"))
                                                errors.write(
                                                        request, response, 401, "UNAUTHENTICATED");
                                            else response.sendRedirect("/console/login");
                                        })
                                .accessDeniedHandler(
                                        (request, response, failure) ->
                                                errors.write(request, response, 403, "FORBIDDEN")));
        if (settings.enabled) {
            var provider = new DaoAuthenticationProvider();
            provider.setUserDetailsService(users);
            provider.setPasswordEncoder(new BCryptPasswordEncoder());
            var login =
                    new UsernamePasswordAuthenticationFilter() {
                        @Override
                        public Authentication attemptAuthentication(
                                HttpServletRequest request, HttpServletResponse response) {
                            if (!request.getMethod().equals("POST"))
                                throw new org.springframework.security.authentication
                                        .AuthenticationServiceException("POST required");
                            var token =
                                    UsernamePasswordAuthenticationToken.unauthenticated(
                                            request.getParameter("username"),
                                            request.getParameter("password"));
                            token.setDetails(authenticationDetailsSource.buildDetails(request));
                            return getAuthenticationManager().authenticate(token);
                        }
                    };
            login.setAuthenticationManager(new ProviderManager(provider));
            login.setRequiresAuthenticationRequestMatcher(
                    new org.springframework.security.web.util.matcher.AntPathRequestMatcher(
                            "/console/login", "POST"));
            login.setSecurityContextRepository(new HttpSessionSecurityContextRepository());
            login.setSessionAuthenticationStrategy(
                    new org.springframework.security.web.authentication.session
                            .CompositeSessionAuthenticationStrategy(
                            java.util.List.of(
                                    new org.springframework.security.web.authentication.session
                                            .ChangeSessionIdAuthenticationStrategy(),
                                    new org.springframework.security.web.csrf
                                            .CsrfAuthenticationStrategy(csrf))));
            login.setAuthenticationSuccessHandler(
                    (request, response, auth) -> response.sendRedirect("/console/"));
            login.setAuthenticationFailureHandler(
                    (request, response, failure) -> response.sendRedirect("/console/login?error"));
            http.addFilterAt(login, UsernamePasswordAuthenticationFilter.class);
        }
        http.logout(
                config ->
                        config.logoutUrl("/console/logout")
                                .logoutSuccessUrl("/console/login")
                                .invalidateHttpSession(true)
                                .deleteCookies("JSESSIONID"));
        http.headers(
                config ->
                        config.contentSecurityPolicy(
                                csp ->
                                        csp.policyDirectives(
                                                "default-src 'self'; script-src 'self'; style-src"
                                                    + " 'self' 'unsafe-inline'; object-src 'none';"
                                                    + " frame-ancestors 'none'")));
        return http.build();
    }
}
