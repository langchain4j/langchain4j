package dev.langchain4j.guardrails.canarytoken;

/**
 * Defines remediation actions when a canary word is detected in the model's output.
 */
public enum CanaryTokenLeakageRemediation {
    /**
     * Block the entire response and return an error message.
     */
    BLOCK,

    /**
     * Redact the canary word by replacing it with a placeholder.
     */
    REDACT,

    /**
     * Throw an exception to be handled by the application.
     */
    THROW_EXCEPTION
}
