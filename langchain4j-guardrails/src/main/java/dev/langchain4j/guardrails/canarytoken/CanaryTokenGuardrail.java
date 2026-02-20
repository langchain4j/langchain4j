package dev.langchain4j.guardrails.canarytoken;

import dev.langchain4j.memory.ChatMemory;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Combined guardrail that manages both input and output canary token protection.
 * <p>
 * This class acts as a container for {@link CanaryTokenInputGuardrail} and {@link CanaryTokenOutputGuardrail},
 * coordinating the canary token value between them. The input guardrail generates and injects the canary token,
 * while the output guardrail validates responses for token leakage.
 * </p>
 * <p>
 * <b>Usage with AiServices:</b>
 * </p>
 * <pre>{@code
 * CanaryTokenGuardrail guardrail = new CanaryTokenGuardrail(config);
 *
 * // Get the input and output components
 * CanaryTokenInputGuardrail inputGuardrail = guardrail.getInputGuardrail();
 * CanaryTokenOutputGuardrail outputGuardrail = guardrail.getOutputGuardrail();
 *
 * // Register them with AiServices
 * AiServices.builder(MyAssistant.class)
 *     .chatModel(model)
 *     .inputGuardrails(inputGuardrail)
 *     .outputGuardrails(outputGuardrail)
 *     .build();
 * }</pre>
 * <p>
 * <b>Note:</b> This class cannot implement both {@code InputGuardrail} and {@code OutputGuardrail}
 * interfaces directly due to Java's type system limitations (conflicting default methods with
 * different return types). Use {@link #getInputGuardrail()} and {@link #getOutputGuardrail()}
 * to get the respective components.
 * </p>
 *
 * @see CanaryTokenInputGuardrail
 * @see CanaryTokenOutputGuardrail
 * @see CanaryTokenGuardrailConfig
 */
public class CanaryTokenGuardrail {

    private final CanaryTokenInputGuardrail inputGuardrail;
    private final CanaryTokenOutputGuardrail outputGuardrail;
    private final CanaryTokenGuardrailConfig config;

    // Instance field for current canary token value
    // The input guardrail sets this value, and the output guardrail reads it
    private String currentCanaryValue;

    // Set to track which ChatMemory instances have already been injected with a canary
    // Using WeakHashMap as a Set to avoid memory leaks (memories can be garbage collected)
    private final Set<ChatMemory> injectedMemories = Collections.newSetFromMap(new WeakHashMap<>());

    /**
     * Default constructor using default configuration.
     */
    public CanaryTokenGuardrail() {
        this(CanaryTokenGuardrailConfig.builder().build());
    }

    /**
     * Constructs a new {@link CanaryTokenGuardrail} with the specified config.
     *
     * @param config the {@link CanaryTokenGuardrailConfig} to use. Must not be null.
     */
    public CanaryTokenGuardrail(CanaryTokenGuardrailConfig config) {
        this.config = ensureNotNull(config, "config");
        this.inputGuardrail = new CanaryTokenInputGuardrail(this);
        this.outputGuardrail = new CanaryTokenOutputGuardrail(this);
    }

    /**
     * Gets the configuration used by this guardrail.
     *
     * @return the {@link CanaryTokenGuardrailConfig}
     */
    public CanaryTokenGuardrailConfig getConfig() {
        return config;
    }

    /**
     * Gets the input guardrail component.
     *
     * @return the {@link CanaryTokenInputGuardrail}
     */
    public CanaryTokenInputGuardrail getInputGuardrail() {
        return inputGuardrail;
    }

    /**
     * Gets the output guardrail component.
     *
     * @return the {@link CanaryTokenOutputGuardrail}
     */
    public CanaryTokenOutputGuardrail getOutputGuardrail() {
        return outputGuardrail;
    }

    /**
     * Gets the current canary token value.
     *
     * @return the current canary token value, or null if not set
     */
    String getCurrentCanaryValue() {
        return currentCanaryValue;
    }

    /**
     * Sets the current canary token value.
     *
     * @param value the canary token value to store
     */
    void setCurrentCanaryValue(String value) {
        this.currentCanaryValue = value;
    }

    /**
     * Checks if a canary token has already been injected for the given ChatMemory.
     *
     * @param memory the ChatMemory to check
     * @return true if canary has been injected for this memory, false otherwise
     */
    boolean isCanaryInjected(ChatMemory memory) {
        return memory != null && injectedMemories.contains(memory);
    }

    /**
     * Marks the canary token as injected for the given ChatMemory.
     *
     * @param memory the ChatMemory that has been injected
     */
    void setCanaryInjected(ChatMemory memory) {
        if (memory != null) {
            injectedMemories.add(memory);
        }
    }
}
