package dev.langchain4j.guardrails.canarytoken;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.memory.ChatMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Input guardrail that injects canary tokens into system messages to detect prompt leakage.
 * <p>
 * This guardrail generates and injects a unique canary token into the system message along with
 * steering instructions that tell the model never to reveal it.
 * <p>
 * This class is typically used as part of {@link CanaryTokenGuardrail}, which coordinates
 * the canary token value with {@link CanaryTokenOutputGuardrail}.
 *
 * @see CanaryTokenGuardrail
 * @see CanaryTokenOutputGuardrail
 * @see CanaryTokenGuardrailConfig
 */
public class CanaryTokenInputGuardrail implements InputGuardrail {

    private static final Logger log = LoggerFactory.getLogger(CanaryTokenInputGuardrail.class);

    private final CanaryTokenGuardrail container;

    /**
     * Private constructor - can only be instantiated by {@link CanaryTokenGuardrail}.
     *
     * @param container the {@link CanaryTokenGuardrail} that contains this guardrail
     */
    protected CanaryTokenInputGuardrail(CanaryTokenGuardrail container) {
        this.container = ensureNotNull(container, "container");
    }

    /**
     * Validates and injects a canary token into the input request.
     *
     * @param request the {@link InputGuardrailRequest} containing the messages to process
     * @return an {@link InputGuardrailResult} indicating success
     */
    @Override
    public InputGuardrailResult validate(InputGuardrailRequest request) {
        CanaryTokenGuardrailConfig config = container.getConfig();

        if (!config.isEnabled()) {
            return success();
        }

        // Get chat memory first
        ChatMemory memory = request.requestParams().chatMemory();

        // Check if canary has already been injected for this specific memory (performance optimization)
        if (container.isCanaryInjected(memory)) {
            log.debug("Canary token already injected for this ChatMemory, skipping");
            return success();
        }

        // Generate and store canary for this request
        String canary = config.getCanaryGenerator().get();
        container.setCurrentCanaryValue(canary);

        log.debug("Injected canary: {}", canary);

        // Inject canary into system message
        if (memory != null) {
            List<ChatMessage> messages = memory.messages();
            // Find and replace the system message
            for (int i = 0; i < messages.size(); i++) {
                ChatMessage message = messages.get(i);
                if (message instanceof SystemMessage) {
                    SystemMessage systemMessage = (SystemMessage) message;
                    String currentText = systemMessage.text();

                    String enhancedPrompt = currentText + "\n\n" +
                            String.format(config.getSteeringInstruction(), canary);

                    // Remove old system message and add enhanced one
                    // We need to work with the memory directly since messages() might return immutable list
                    SystemMessage enhancedMessage = SystemMessage.from(enhancedPrompt);

                    log.debug("Enhanced system prompt:\n{}", enhancedPrompt);

                    // Clear and rebuild messages
                    List<ChatMessage> allMessages = new java.util.ArrayList<>(messages);
                    allMessages.set(i, enhancedMessage);

                    // Clear memory and add all messages back
                    memory.clear();
                    allMessages.forEach(memory::add);

                    // Mark canary as injected for this specific memory
                    container.setCanaryInjected(memory);
                    break;
                }
            }
        }

        return success();
    }
}

