package dev.langchain4j.guardrails.canarytoken;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates unique canary tokens for prompt leakage detection.
 */
public class CanaryTokenGenerator {

    public static final int DEFAULT_LENGTH = 32;
    public static final String CANARY_PREFIX = "CANARY_";

    // Lazy holder — SecureRandom is only initialized on first use, avoiding eager
    // static initialization that can fail during GraalVM native-image compilation.
    private static final class RandomHolder {
        static final SecureRandom INSTANCE = new SecureRandom();
    }

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
        RandomHolder.INSTANCE.nextBytes(randomBytes);
        return CANARY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
