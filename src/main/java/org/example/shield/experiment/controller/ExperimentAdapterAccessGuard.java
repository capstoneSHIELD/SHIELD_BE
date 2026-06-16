package org.example.shield.experiment.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

@Component
@ConditionalOnProperty(prefix = "shield.experiment.adapter", name = "enabled", havingValue = "true")
public class ExperimentAdapterAccessGuard {

    public static final String HEADER_NAME = "X-SHIELD-EXPERIMENT-TOKEN";

    private final String accessToken;

    public ExperimentAdapterAccessGuard(
            @Value("${shield.experiment.adapter.access-token:}") String accessToken,
            Environment environment
    ) {
        this.accessToken = accessToken == null ? "" : accessToken.trim();
        if (!isLocalOrTest(environment) && this.accessToken.isBlank()) {
            throw new IllegalStateException(
                    "shield.experiment.adapter.access-token is required outside local/test profiles"
            );
        }
    }

    public void verify(String suppliedToken) {
        if (accessToken.isBlank()) {
            return;
        }
        String supplied = suppliedToken == null ? "" : suppliedToken.trim();
        if (!constantTimeEquals(accessToken, supplied)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid experiment adapter token");
        }
    }

    private static boolean isLocalOrTest(Environment environment) {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "local".equals(profile) || "test".equals(profile));
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }
}
