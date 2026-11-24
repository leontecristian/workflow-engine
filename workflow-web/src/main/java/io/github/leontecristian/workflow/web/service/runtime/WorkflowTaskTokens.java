package io.github.leontecristian.workflow.web.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.WorkflowPersonalTaskCursor;
import io.github.leontecristian.workflow.web.exception.runtime.WorkflowRuntimeException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Stateless opaque version and pagination tokens, scoped to the authorized task and actor. */
public final class WorkflowTaskTokens {
    public static final String PRECONDITION_HEADER = "X-Task-Precondition";
    private static final int NONCE_BYTES = 12;
    private static final int ENCODED_LENGTH = 48;
    private static final int PERSONAL_ENCODED_LENGTH = 64;
    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;

    public WorkflowTaskTokens(String encodedKey) {
        byte[] keyBytes;
        if (encodedKey == null) {
            keyBytes = new byte[32];
            random.nextBytes(keyBytes);
        } else {
            try {
                keyBytes = Base64.getUrlDecoder().decode(encodedKey);
                if (keyBytes.length != 32) throw new IllegalArgumentException();
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("Runtime task token key must be 32 Base64url-encoded bytes");
            }
        }
        key = new SecretKeySpec(keyBytes, "AES");
    }

    public String precondition(String actorRef, long taskId, long version) {
        return encode("task-version", actorRef, taskId, version);
    }

    public long expectedVersion(String token, String actorRef, long taskId) {
        return decode("task-version", token, actorRef, taskId);
    }

    public String continuation(String actorRef, long taskId, long sequence) {
        return encode("previous-decisions", actorRef, taskId, sequence);
    }

    public long after(String token, String actorRef, long taskId) {
        return token == null ? 0 : decode("previous-decisions", token, actorRef, taskId);
    }

    public String taskPageCursor(String list, String actorRef, long scopeId, long afterId) {
        return encode("task-list-" + list, actorRef, scopeId, afterId);
    }

    public long taskPageAfter(String list, String token, String actorRef, long scopeId) {
        return token == null ? 0 : decode("task-list-" + list, token, actorRef, scopeId);
    }

    public String personalTaskPageCursor(String actorRef, String criteria, WorkflowPersonalTaskCursor cursor) {
        byte[] position = ByteBuffer.allocate(2 * Long.BYTES + Integer.BYTES)
                .putLong(cursor.createdAt().getEpochSecond()).putInt(cursor.createdAt().getNano())
                .putLong(cursor.taskId()).array();
        return encode("task-list-mine-created-desc:" + criteria, actorRef, 0, position);
    }

    public WorkflowPersonalTaskCursor personalTaskPageAfter(String token, String actorRef, String criteria) {
        if (token == null) return null;
        var position = decode("task-list-mine-created-desc:" + criteria, token, actorRef, 0,
                PERSONAL_ENCODED_LENGTH);
        return new WorkflowPersonalTaskCursor(Instant.ofEpochSecond(position.getLong(), position.getInt()),
                position.getLong());
    }

    private String encode(String purpose, String actorRef, long taskId, long value) {
        return encode(purpose, actorRef, taskId, ByteBuffer.allocate(Long.BYTES).putLong(value).array());
    }

    private String encode(String purpose, String actorRef, long taskId, byte[] value) {
        var nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            byte[] ciphertext = cipher(Cipher.ENCRYPT_MODE, purpose, actorRef, taskId, nonce)
                    .doFinal(value);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    ByteBuffer.allocate(nonce.length + ciphertext.length).put(nonce).put(ciphertext).array());
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Could not issue workflow task token", failure);
        }
    }

    private long decode(String purpose, String token, String actorRef, long taskId) {
        return decode(purpose, token, actorRef, taskId, ENCODED_LENGTH).getLong();
    }

    private ByteBuffer decode(String purpose, String token, String actorRef, long taskId, int encodedLength) {
        if (token == null || token.length() != encodedLength) throw WorkflowRuntimeException.invalid();
        try {
            var data = ByteBuffer.wrap(Base64.getUrlDecoder().decode(token));
            var nonce = new byte[NONCE_BYTES];
            data.get(nonce);
            byte[] plaintext = cipher(Cipher.DECRYPT_MODE, purpose, actorRef, taskId, nonce)
                    .doFinal(data.array(), NONCE_BYTES, data.remaining());
            return ByteBuffer.wrap(plaintext);
        } catch (GeneralSecurityException | IllegalArgumentException invalid) {
            throw WorkflowRuntimeException.invalid();
        }
    }

    private Cipher cipher(int mode, String purpose, String actorRef, long taskId, byte[] nonce)
            throws GeneralSecurityException {
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(128, nonce));
        cipher.updateAAD((purpose + ":" + taskId + ":" + actorRef).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}
