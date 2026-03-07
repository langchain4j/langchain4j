package dev.langchain4j.guardrails.canarytoken;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Output guardrail that validates AI responses for canary token leakage.
 * <p>
 * This guardrail is <b>stateless</b>: it reads the canary token value that was stored by
 * {@link CanaryTokenInputGuardrail} via {@link CanaryTokenState#store(GuardrailRequestParams, CanaryTokenState)},
 * and resolves its {@link CanaryTokenGuardrailConfig} via
 * {@link CanaryTokenGuardrailConfig#from(GuardrailRequestParams)}.
 * Neither this class nor {@link CanaryTokenInputGuardrail} reference {@code InvocationContext}
 * or {@code LangChain4jManaged} directly - all transport is encapsulated behind
 * {@link GuardrailRequestParams}.
 * </p>
 * <p>
 * Config resolution order (first match wins):
 * <ol>
 *   <li>Config present in the invocation-scoped managed parameters (annotation-based wiring)</li>
 *   <li>Config supplied at construction time (programmatic wiring)</li>
 *   <li>Built-in defaults (BLOCK remediation, enabled)</li>
 * </ol>
 * </p>
 * <p>
 * Both guardrails can be registered independently - no stateful container
 * is required and annotation-based wiring works naturally:
 * </p>
 * <pre>{@code
 * @InputGuardrails(CanaryTokenInputGuardrail.class)
 * @OutputGuardrails(CanaryTokenOutputGuardrail.class)
 * String chat(String message);
 * }</pre>
 *
 * @see CanaryTokenInputGuardrail
 * @see CanaryTokenGuardrailConfig
 * @see CanaryTokenState
 */
public class CanaryTokenOutputGuardrail implements OutputGuardrail {

    private static final Logger log = LoggerFactory.getLogger(CanaryTokenOutputGuardrail.class);

    /**
     * Fallback config used when no config is found in the invocation-scoped managed parameters.
     * May be {@code null} to use built-in defaults.
     */
    private final CanaryTokenGuardrailConfig constructorConfig;

    /**
     * No-arg constructor for annotation-based wiring.
     * Uses built-in defaults unless a {@link CanaryTokenGuardrailConfig} is present in the
     * invocation-scoped managed parameters at validation time.
     */
    public CanaryTokenOutputGuardrail() {
        this(null);
    }

    /**
     * Constructor for programmatic wiring with a fixed config.
     * The config supplied here is used as fallback if no config is found in the
     * invocation-scoped managed parameters.
     *
     * @param config the fallback configuration, or {@code null} to use built-in defaults
     */
    public CanaryTokenOutputGuardrail(CanaryTokenGuardrailConfig config) {
        this.constructorConfig = config;
    }

    /**
     * Validates the model's response for canary token leakage.
     * <p>
     * The canary value is retrieved via {@link CanaryTokenState#from(GuardrailRequestParams)}
     * where {@link CanaryTokenInputGuardrail} stored it during the same invocation.
     * </p>
     *
     * @param request the {@link OutputGuardrailRequest} containing the AI's response
     * @return an {@link OutputGuardrailResult} with the validated or remediated response
     */
    @Override
    public OutputGuardrailResult validate(OutputGuardrailRequest request) {
        GuardrailRequestParams params = request.requestParams();
        CanaryTokenGuardrailConfig config = resolveConfig(params);

        if (config.isDisabled()) {
            return success();
        }

        Optional<CanaryTokenState> state = CanaryTokenState.from(params);
        if (state.isEmpty()) {
            // No canary was stored - input guardrail didn't run or was disabled
            return success();
        }

        String canary = state.get().canaryValue();
        AiMessage aiMessage = request.responseFromLLM().aiMessage();
        String content = aiMessage.text();

        if (content == null || !content.contains(canary)) {
            return success();
        }

        // Leakage detected - apply remediation
        log.debug("Guardrail detected system prompt leakage in response: {}", content);

        return switch (config.getRemediation()) {
            case BLOCK -> {
                AiMessage blockedMessage = AiMessage.from(config.getBlockedMessage());
                yield successWith(blockedMessage);
            }
            case REDACT -> {
                String redactedContent = content.replace(canary, config.getRedactionPlaceholder());
                AiMessage redactedMessage = AiMessage.from(redactedContent);
                log.debug("Original response: {}", content);
                log.debug("Redacted response: {}", redactedContent);
                yield successWith(redactedMessage);
            }
            case THROW_EXCEPTION -> {
                log.debug("Guardrail detected leakage!");
                yield fatal("System prompt leakage detected", new CanaryTokenLeakageException(canary, content));
            }
        };
    }

    /**
     * Resolves the {@link CanaryTokenGuardrailConfig} for this invocation.
     * <ol>
     *   <li>Checks the invocation-scoped managed parameters first via
     *       {@link CanaryTokenGuardrailConfig#fromManaged(GuardrailRequestParams)}.</li>
     *   <li>Falls back to {@link #constructorConfig} if set.</li>
     *   <li>Finally falls back to built-in defaults.</li>
     * </ol>
     */
    private CanaryTokenGuardrailConfig resolveConfig(GuardrailRequestParams params) {
        CanaryTokenGuardrailConfig managedConfig = CanaryTokenGuardrailConfig.fromManaged(params);
        if (managedConfig != null) {
            return managedConfig;
        }
        return constructorConfig != null
                ? constructorConfig
                : CanaryTokenGuardrailConfig.builder().build();
    }
}
