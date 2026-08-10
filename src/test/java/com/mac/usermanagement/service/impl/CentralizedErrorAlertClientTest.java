package com.mac.usermanagement.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.mac.usermanagement.config.properties.ErrorAlertProperties;
import com.mac.usermanagement.entities.model.ErrorAlert;
import com.mac.usermanagement.service.HttpTransport;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CentralizedErrorAlertClientTest {

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    @Test
    void sendsExpectedAlertRequestAndHandlesNonSuccess() {
        List<HttpRequest> requests = new ArrayList<>();
        HttpTransport transport = request -> {
            requests.add(request);
            return requests.size() == 1 ? 202 : 503;
        };
        CentralizedErrorAlertClient client = new CentralizedErrorAlertClient(
                transport, new ObjectMapper(), properties(true, List.of("ops@example.com"), "Bearer token"));

        client.send(ErrorAlert.failure("trace-1", "http", "request"));
        client.send(ErrorAlert.failure("trace-2", "http", "request"));

        assertThat(requests).hasSize(2);
        assertThat(requests.getFirst().headers().firstValue("Authorization")).contains("Bearer token");
        assertThat(requests.getFirst().headers().firstValue("X-Correlation-Id")).contains("trace-1");
    }

    @Test
    void skipsDisabledOrEmptyRecipientsAndContainsTransportFailures() {
        List<HttpRequest> requests = new ArrayList<>();
        HttpTransport recording = request -> {
            requests.add(request);
            return 202;
        };
        ErrorAlert alert = ErrorAlert.failure("trace", "http", "request");
        new CentralizedErrorAlertClient(recording, new ObjectMapper(),
                properties(false, List.of("ops@example.com"), "")).send(alert);
        new CentralizedErrorAlertClient(recording, new ObjectMapper(),
                properties(true, List.of(), "")).send(alert);
        assertThat(requests).isEmpty();

        new CentralizedErrorAlertClient(request -> {
            throw new IOException("down");
        }, new ObjectMapper(), properties(true, List.of("ops@example.com"), "")).send(alert);
        new CentralizedErrorAlertClient(request -> {
            throw new InterruptedException("stop");
        }, new ObjectMapper(), properties(true, List.of("ops@example.com"), "")).send(alert);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    private static ErrorAlertProperties properties(boolean enabled, List<String> recipients, String auth) {
        return new ErrorAlertProperties(enabled, URI.create("https://alert.example.com/api/v1/alert"),
                "usermanagement@example.com", "User Management", recipients, auth, Duration.ofSeconds(2));
    }
}
