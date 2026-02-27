package dev.langchain4j.guardrails.canarytoken;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.LangChain4jManaged;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Output guardrail that validates AI responses for canary token leakage.
 * <p>
 * This guardrail is <b>stateless</b>: it reads the canary token value that was stored by
 * {@link CanaryTokenInputGuardrail} in {@link InvocationContext#managedParameters()} under
 * {@link CanaryTokenState}, and reads its {@link CanaryTokenGuardrailConfig} from the same map
 * under the key {@code CanaryTokenGuardrailConfig.class}.
 * </p>
 * <p>
 * Config resolution order (first match wins):
 * <ol>
 *   <li>{@link InvocationContext#managedParameters()} — keyed by {@code CanaryTokenGuardrailConfig.class}</li>
 *   <li>Config supplied at construction time (if any)</li>
 *   <li>Built-in defaults (BLOCK remediation, enabled)</li>
 * </ol>
 * </p>
 * <p>
 * Both guardrails can be registered independently — no stateful container
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

    /** Fallback config when nothing is found in managedParameters. May be null (use built-in defaults). */
    private final CanaryTokenGuardrailConfig constructorConfig;

    /**
     * No-arg constructor for annotation-based wiring.
     * Uses built-in defaults unless a {@link CanaryTokenGuardrailConfig} is present in
     * {@link InvocationContext#managedParameters()} at validation time.
     */
    public CanaryTokenOutputGuardrail() {
        this(null);
    }

    /**
     * Constructor for programmatic wiring with a fixed config.
     * The config supplied here is used as fallback if no config is found in
     * {@link InvocationContext#managedParameters()}.
     *
     * @param config the fallback configuration, or {@code null} to use built-in defaults
     */
    public CanaryTokenOutputGuardrail(CanaryTokenGuardrailConfig config) {
        this.constructorConfig = config;
    }

    /**
     * Validates the model's response for canary token leakage.
     * <p>
     * The canary value is retrieved from {@link InvocationContext#managedParameters()}
     * where {@link CanaryTokenInputGuardrail} stored it during the same invocation.
     * </p>
     *
     * @param request the {@link OutputGuardrailRequest} containing the AI's response
     * @return an {@link OutputGuardrailResult} with the validated or remediated response
     */
    @Override
    public OutputGuardrailResult validate(OutputGuardrailRequest request) {
        CanaryTokenGuardrailConfig config = resolveConfig(request);

        if (config.isDisabled()) {
            return success();
        }

        String canary = resolveCanaryValue(request);
        if (canary == null) {
            // No canary was stored — input guardrail didn't run or was disabled
            return success();
        }

        AiMessage aiMessage = request.responseFromLLM().aiMessage();
        String content = aiMessage.text();

        if (content == null || !content.contains(canary)) {
            return success();
        }

        // Leakage detected — apply remediation
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
     *   <li>Checks {@link InvocationContext#managedParameters()} first.</li>
     *   <li>Falls back to {@link #constructorConfig} if set.</li>
     *   <li>Finally falls back to built-in defaults.</li>
     * </ol>
     */
    private CanaryTokenGuardrailConfig resolveConfig(OutputGuardrailRequest request) {
        Map<Class<? extends LangChain4jManaged>, LangChain4jManaged> managed =
                managedMap(request.requestParams().invocationContext());
        if (managed != null) {
            LangChain4jManaged value = managed.get(CanaryTokenGuardrailConfig.class);
            if (value instanceof CanaryTokenGuardrailConfig cfg) {
                return cfg;
            }
        }
        return constructorConfig != null
                ? constructorConfig
                : CanaryTokenGuardrailConfig.builder().build();
    }

    /**
     * Retrieves the canary value stored by {@link CanaryTokenInputGuardrail}
     * in {@link InvocationContext#managedParameters()}.
     *
     * @return the canary value, or {@code null} if not found
     */
    private static String resolveCanaryValue(OutputGuardrailRequest request) {
        Map<Class<? extends LangChain4jManaged>, LangChain4jManaged> managed =
                managedMap(request.requestParams().invocationContext());
        if (managed == null) {
            return null;
        }
        LangChain4jManaged state = managed.get(CanaryTokenState.class);
        return (state instanceof CanaryTokenState canaryState) ? canaryState.canaryValue() : null;
    }

    private static Map<Class<? extends LangChain4jManaged>, LangChain4jManaged> managedMap(
            InvocationContext invocationContext) {
        if (invocationContext == null) {
            return null;
        }
        return invocationContext.managedParameters();
    }
}
