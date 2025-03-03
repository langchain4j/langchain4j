package dev.langchain4j.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatExecutor;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.AugmentationResult;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Represents the parameter passed to {@link OutputGuardrail#validate(OutputGuardrailParams)}.
 *
 * @param responseFromLLM
 *            the response from the LLM
 * @param chatMemory
 *            the memory
 * @param augmentationResult
 *            the augmentation result
 * @param userMessageTemplate
 *            the user message template
 * @param variables
 *            the variable to be used with userMessageTemplate
 */
public record OutputGuardrailParams(
        ChatResponse responseFromLLM,
        ChatExecutor chatExecutor,
        @Nullable ChatMemory chatMemory,
        @Nullable AugmentationResult augmentationResult,
        String userMessageTemplate,
        Map<String, Object> variables)
        implements GuardrailParams<OutputGuardrailParams> {

    public OutputGuardrailParams {
        ensureNotNull(responseFromLLM, "responseFromLLM");
        ensureNotNull(userMessageTemplate, "userMessageTemplate");
        ensureNotNull(variables, "variables");
        ensureNotNull(chatExecutor, "chatExecutor");
    }

    @Override
    public OutputGuardrailParams withText(String text) {
        ensureNotNull(text, "text");

        var aiMessage = Optional.ofNullable(this.responseFromLLM.aiMessage().toolExecutionRequests())
                .filter(t -> !t.isEmpty())
                .map(t -> new AiMessage(text, t))
                .orElseGet(() -> new AiMessage(text));

        var chatResponse = ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(this.responseFromLLM.metadata())
                .build();

        return new OutputGuardrailParams(
                chatResponse,
                this.chatExecutor,
                this.chatMemory,
                this.augmentationResult,
                this.userMessageTemplate,
                this.variables);
    }
}
