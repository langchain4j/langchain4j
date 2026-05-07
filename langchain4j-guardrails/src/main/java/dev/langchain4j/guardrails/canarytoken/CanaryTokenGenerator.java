package dev.langchain4j.guardrails.canarytoken;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Generates unique canary tokens for prompt leakage detection.
 */
public class CanaryTokenGenerator {

    private static final AtomicReference<SecureRandom> RANDOM = new AtomicReference<>();
    public static final int DEFAULT_LENGTH = 32;
    public static final String CANARY_PREFIX = "CANARY_";

    /**
     * Generates a random base64-encoded canary token.
     */
    public static String generateDefault() {
        return generate(DEFAULT_LENGTH);
    }

    /**
     * Generates a random base64-encoded canary token of specified byte length.
     */
    public static String generate(int byteLength) {
        byte[] randomBytes = new byte[byteLength];
        random().nextBytes(randomBytes);
        return CANARY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private static SecureRandom random() {
        return RANDOM.updateAndGet(r -> r != null ? r : new SecureRandom());
    }
}
