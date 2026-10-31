package io.github.leontecristian.workflow.testhost.service.sandbox;

import io.github.leontecristian.workflow.core.dto.runtime.HostStartIntent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class SandboxStartIdentity {
    private SandboxStartIdentity() {}

    public static HostStartIntent intent(long requestId, long expected) {
        if (requestId <= 0 || expected < 0)
            throw new IllegalArgumentException("Invalid sandbox intent");
        String canonical =
                "{\"expectedRequestVersion\":\""
                        + expected
                        + "\",\"requestId\":\""
                        + requestId
                        + "\"}";
        try {
            return new HostStartIntent(
                    "wf-test-start-v1",
                    HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(canonical.getBytes(StandardCharsets.UTF_8))));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
