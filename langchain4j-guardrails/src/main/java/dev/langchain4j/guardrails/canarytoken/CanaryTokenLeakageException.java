package dev.langchain4j.guardrails.canarytoken;

/**
 * Exception thrown when a canary token is detected in the model's output,
 * indicating a system prompt leakage.
 */
public class CanaryTokenLeakageException extends RuntimeException {

    private final String canaryToken;
    private final String leakedContent;

    public CanaryTokenLeakageException(String canaryToken, String leakedContent) {
        super("System prompt leakage detected: canary token found in model output");
        this.canaryToken = canaryToken;
        this.leakedContent = leakedContent;
    }

    public String getCanaryToken() {
        return canaryToken;
    }

    public String getLeakedContent() {
        return leakedContent;
    }
}

