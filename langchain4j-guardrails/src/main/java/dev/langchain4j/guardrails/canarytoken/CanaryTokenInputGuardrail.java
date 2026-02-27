package dev.langchain4j.guardrails.canarytoken;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.LangChain4jManaged;
import dev.langchain4j.memory.ChatMemory;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Input guardrail that injects a canary token into the system message to detect prompt leakage.
 * <p>
 * This guardrail is <b>stateless</b>: it reads its {@link CanaryTokenGuardrailConfig} and stores
 * the generated canary value entirely through
 * {@link InvocationContext#managedParameters()}, so that
 * {@link CanaryTokenOutputGuardrail} can read the same value without any shared mutable state
 * between the two guardrail instances.
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
 * <b>Annotation usage (framework wires instances, defaults apply):</b>
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

    /** Fallback config when nothing is found in managedParameters. May be null (use built-in defaults). */
    private final CanaryTokenGuardrailConfig constructorConfig;

    /**
     * No-arg constructor for annotation-based wiring.
     * Uses built-in defaults unless a {@link CanaryTokenGuardrailConfig} is present in
     * {@link InvocationContext#managedParameters()} at validation time.
     */
    public CanaryTokenInputGuardrail() {
        this(null);
    }

    /**
     * Constructor for programmatic wiring with a fixed config.
     * The config supplied here is used as fallback if no config is found in
     * {@link InvocationContext#managedParameters()}.
     *
     * @param config the fallback configuration, or {@code null} to use built-in defaults
     */
    public CanaryTokenInputGuardrail(CanaryTokenGuardrailConfig config) {
        this.constructorConfig = config;
    }

    /**
     * Validates and injects a canary token into the input request.
     * <p>
     * The canary value is generated once per invocation and stored in
     * {@link InvocationContext#managedParameters()} under {@link CanaryTokenState},
     * so it can be retrieved by {@link CanaryTokenOutputGuardrail} later in the same
     * invocation without any shared instance state.
     * </p>
     *
     * @param request the {@link InputGuardrailRequest} containing the messages to process
     * @return an {@link InputGuardrailResult} indicating success
     */
    @Override
    public InputGuardrailResult validate(InputGuardrailRequest request) {
        CanaryTokenGuardrailConfig config = resolveConfig(request);

        if (config.isDisabled()) {
            return success();
        }

        // Check if canary has already been injected for this invocation
        InvocationContext invocationContext = request.requestParams().invocationContext();
        Map<Class<? extends LangChain4jManaged>, LangChain4jManaged> managed = managedMap(invocationContext);
        if (managed != null && managed.containsKey(CanaryTokenState.class)) {
            log.debug("Canary token already injected for this invocation, skipping");
            return success();
        }

        // Generate canary and store it in InvocationContext so the output guardrail can read it
        String canary = config.getCanaryGenerator().get();
        if (managed != null) {
            managed.put(CanaryTokenState.class, new CanaryTokenState(canary));
        }

        log.debug("Injected canary: {}", canary);

        // Inject canary into system message
        ChatMemory memory = request.requestParams().chatMemory();
        if (memory != null) {
            List<ChatMessage> messages = memory.messages();
            for (int i = 0; i < messages.size(); i++) {
                ChatMessage message = messages.get(i);
                if (message instanceof SystemMessage systemMessage) {
                    String enhancedPrompt =
                            systemMessage.text() + "\n\n" + String.format(config.getSteeringInstruction(), canary);

                    log.debug("Enhanced system prompt:\n{}", enhancedPrompt);

                    List<ChatMessage> allMessages = new java.util.ArrayList<>(messages);
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
     *   <li>Checks {@link InvocationContext#managedParameters()} first.</li>
     *   <li>Falls back to {@link #constructorConfig} if set.</li>
     *   <li>Finally falls back to built-in defaults.</li>
     * </ol>
     */
    private CanaryTokenGuardrailConfig resolveConfig(InputGuardrailRequest request) {
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

    private static Map<Class<? extends LangChain4jManaged>, LangChain4jManaged> managedMap(
            InvocationContext invocationContext) {
        if (invocationContext == null) {
            return null;
        }
        return invocationContext.managedParameters();
    }
}
