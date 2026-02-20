package dev.langchain4j.guardrails.canarytoken;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Output guardrail that validates AI responses for canary token leakage.
 * <p>
 * This guardrail checks if the canary token appears in the AI's response and applies
 * the configured remediation strategy (BLOCK, REDACT, or THROW_EXCEPTION).
 * <p>
 * This class is typically used as part of {@link CanaryTokenGuardrail}, which coordinates
 * the canary token value with {@link CanaryTokenInputGuardrail}.
 *
 * @see CanaryTokenGuardrail
 * @see CanaryTokenInputGuardrail
 * @see CanaryTokenGuardrailConfig
 */
public class CanaryTokenOutputGuardrail implements OutputGuardrail {

    private static final Logger log = LoggerFactory.getLogger(CanaryTokenOutputGuardrail.class);

    private final CanaryTokenGuardrail container;

    /**
     * Private constructor - can only be instantiated by {@link CanaryTokenGuardrail}.
     *
     * @param container the {@link CanaryTokenGuardrail} that contains this guardrail
     */
    protected CanaryTokenOutputGuardrail(CanaryTokenGuardrail container) {
        this.container = ensureNotNull(container, "container");
    }

    /**
     * Validates the model's response for canary token leakage.
     *
     * @param request the {@link OutputGuardrailRequest} containing the AI's response
     * @return an {@link OutputGuardrailResult} with the validated or remediated response
     */
    @Override
    public OutputGuardrailResult validate(OutputGuardrailRequest request) {
        CanaryTokenGuardrailConfig config = container.getConfig();

        if (!config.isEnabled()) {
            return success();
        }

        String canary = container.getCurrentCanaryValue();
        if (canary == null) {
            // No canary was set (shouldn't happen in normal flow)
            return success();
        }

        AiMessage aiMessage = request.responseFromLLM().aiMessage();
        String content = aiMessage.text();

        if (content == null || !content.contains(canary)) {
            // No leakage detected
            return success();
        }

        // Leakage detected - apply remediation
        log.debug("Guardrail detected system prompt leakage in response: {}", content);

        switch (config.getRemediation()) {
            case BLOCK:
                AiMessage blockedMessage = AiMessage.from(config.getBlockedMessage());
                return successWith(blockedMessage);

            case REDACT:
                String redactedContent = content.replace(canary, config.getRedactionPlaceholder());
                AiMessage redactedMessage = AiMessage.from(redactedContent);
                log.debug("Original response: {}", content);
                log.debug("Redacted response: {}", redactedContent);
                return successWith(redactedMessage);

            case THROW_EXCEPTION:
                log.debug("Guardrail detected leakage!");
                return fatal(
                        "System prompt leakage detected",
                        new CanaryTokenLeakageException(canary, content)
                );

            default:
                return success();
        }
    }
}

