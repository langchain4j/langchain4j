package dev.langchain4j.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.Optional;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatExecutor;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Represents the parameter passed to {@link OutputGuardrail#validate(OutputGuardrailRequest)}.
 *
 * @param responseFromLLM
 *            the response from the LLM
 * @param commonParams
 *            the set of common params
 */
public record OutputGuardrailRequest(
        ChatResponse responseFromLLM, ChatExecutor chatExecutor, CommonGuardrailParams commonParams)
        implements GuardrailRequest<OutputGuardrailRequest> {

    public OutputGuardrailRequest {
        ensureNotNull(responseFromLLM, "responseFromLLM");
        ensureNotNull(commonParams, "commonParams");
        ensureNotNull(chatExecutor, "chatExecutor");
    }

    @Override
    public OutputGuardrailRequest withText(String text) {
        ensureNotNull(text, "text");

        var aiMessage = Optional.ofNullable(this.responseFromLLM.aiMessage().toolExecutionRequests())
                .filter(t -> !t.isEmpty())
                .map(t -> new AiMessage(text, t))
                .orElseGet(() -> new AiMessage(text));

        var chatResponse = ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(this.responseFromLLM.metadata())
                .build();

        return new OutputGuardrailRequest(chatResponse, this.chatExecutor, this.commonParams);
    }
}
