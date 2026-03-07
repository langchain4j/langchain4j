package dev.langchain4j.guardrails.canarytoken;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.memory.ChatMemory;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Input guardrail that injects a canary token into the system message to detect prompt leakage.
 * <p>
 * This guardrail is <b>stateless</b>: it reads its {@link CanaryTokenGuardrailConfig} and stores
 * the generated canary value entirely through {@link GuardrailRequestParams}, delegating all
 * transport concerns to {@link CanaryTokenGuardrailConfig#from(GuardrailRequestParams)} and
 * {@link CanaryTokenState#store(GuardrailRequestParams, CanaryTokenState)}.
 * Neither this class nor {@link CanaryTokenOutputGuardrail} reference
 * {@code InvocationContext} or {@code LangChain4jManaged} directly - that plumbing is
 * encapsulated inside {@link CanaryTokenState} and {@link CanaryTokenGuardrailConfig}.
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
 * <b>Annotation usage:</b>
 * <pre>{@code
 * @InputGuardrails(CanaryTokenInputGuardrail.class)
 * @OutputGuardrails(CanaryTokenOutputGuardrail.class)
 * String chat(String message);
 * }</pre>
 * </p>
 * <p>
 * <b>Programmatic usage with custom config:</b>
 * <pre>{@code
 * CanaryTokenGuardrailConfig config = CanaryTokenGuardrailConfig.builder()
 *     .remediation(CanaryTokenLeakageRemediation.REDACT)
 *     .build();
 *
 * AiServices.builder(MyAssistant.class)
 *     .chatModel(model)
 *     .inputGuardrails(new CanaryTokenInputGuardrail(config))
 *     .outputGuardrails(new CanaryTokenOutputGuardrail(config))
 *     .build();
 * }</pre>
 * </p>
 *
 * @see CanaryTokenOutputGuardrail
 * @see CanaryTokenGuardrailConfig
 * @see CanaryTokenState
 */
public class CanaryTokenInputGuardrail implements InputGuardrail {

    private static final Logger log = LoggerFactory.getLogger(CanaryTokenInputGuardrail.class);

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
    public CanaryTokenInputGuardrail() {
        this(null);
    }

    /**
     * Constructor for programmatic wiring with a fixed config.
     * The config supplied here is used as fallback if no config is found in the
     * invocation-scoped managed parameters.
     *
     * @param config the fallback configuration, or {@code null} to use built-in defaults
     */
    public CanaryTokenInputGuardrail(CanaryTokenGuardrailConfig config) {
        this.constructorConfig = config;
    }

    /**
     * Validates and injects a canary token into the input request.
     * <p>
     * The canary value is generated once per invocation and stored via
     * {@link CanaryTokenState#store(GuardrailRequestParams, CanaryTokenState)},
     * so it can be retrieved by {@link CanaryTokenOutputGuardrail} later in the same invocation
     * without any shared instance state.
     * </p>
     *
     * @param request the {@link InputGuardrailRequest} containing the messages to process
     * @return an {@link InputGuardrailResult} indicating success
     */
    @Override
    public InputGuardrailResult validate(InputGuardrailRequest request) {
        GuardrailRequestParams params = request.requestParams();
        CanaryTokenGuardrailConfig config = resolveConfig(params);

        if (config.isDisabled()) {
            return success();
        }

        // Idempotency: skip if canary already injected for this invocation
        if (CanaryTokenState.isPresent(params)) {
            log.debug("Canary token already injected for this invocation, skipping");
            return success();
        }

        // Generate canary and store it via GuardrailRequestParams so the output guardrail can read it
        String canary = config.getCanaryGenerator().get();
        CanaryTokenState.store(params, new CanaryTokenState(canary));

        log.debug("Injected canary: {}", canary);

        // Inject canary into system message
        ChatMemory memory = params.chatMemory();
        if (memory != null) {
            List<ChatMessage> messages = memory.messages();
            for (int i = 0; i < messages.size(); i++) {
                if (messages.get(i) instanceof SystemMessage systemMessage) {
                    String enhancedPrompt = systemMessage.text()
                            + "\n\n"
                            + String.format(config.getSteeringInstruction(), canary);

                    log.debug("Enhanced system prompt:\n{}", enhancedPrompt);

                    List<ChatMessage> allMessages = new ArrayList<>(messages);
                    allMessages.set(i, SystemMessage.from(enhancedPrompt));

                    memory.clear();
                    allMessages.forEach(memory::add);
                    break;
                }
            }
        }

        return success();
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
        return constructorConfig != null ? constructorConfig : CanaryTokenGuardrailConfig.builder().build();
    }
}
