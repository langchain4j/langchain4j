package dev.langchain4j.guardrail;

import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.Experimental;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.Optional;

/**
 * Represents the parameter passed to {@link OutputGuardrail#validate(OutputGuardrailRequest)}.
 */
public final class OutputGuardrailRequest implements GuardrailRequest<OutputGuardrailRequest> {
    private final ChatResponse responseFromLLM;
    private final ChatExecutor chatExecutor;
    private final GuardrailRequestParams requestParams;
    private final ChatRequest chatRequest;

    private OutputGuardrailRequest(Builder builder) {
        this.responseFromLLM = ensureNotNull(builder.responseFromLLM, "responseFromLLM");
        this.requestParams = ensureNotNull(builder.requestParams, "requestParams");
        this.chatExecutor = ensureNotNull(builder.chatExecutor, "chatExecutor");
        this.chatRequest = builder.chatRequest;
    }

    /**
     * Returns the response from the LLM.
     *
     * @return the response from the LLM
     */
    public ChatResponse responseFromLLM() {
        return responseFromLLM;
    }

    /**
     * Returns the request that was sent to the model to produce {@link #responseFromLLM()}, if known: when the
     * response is the result of a tool-calling loop, the last request of the loop, whose messages include the tool
     * calls and tool results of all rounds; when the model was asked again after a guardrail failure (reprompt), the
     * request of that call, whose messages include the reprompt. The request is not known, for example, when that
     * call was made by a {@link ChatExecutor} that does not override {@link ChatExecutor#exchange(List)}.
     *
     * @return the request, or {@code null} if it is not known
     * @since 1.23.0
     */
    @Experimental
    public ChatRequest chatRequest() {
        return chatRequest;
    }

    /**
     * Returns the chat executor.
     *
     * @return the chat executor
     */
    public ChatExecutor chatExecutor() {
        return chatExecutor;
    }

    /**
     * Returns the common parameters that are shared across guardrail checks.
     *
     * @return an instance of {@code GuardrailRequestParams} containing shared parameters
     */
    @Override
    public GuardrailRequestParams requestParams() {
        return requestParams;
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

        return builder()
                .responseFromLLM(chatResponse)
                .chatExecutor(this.chatExecutor)
                .requestParams(this.requestParams)
                .chatRequest(this.chatRequest)
                .build();
    }

    /**
     * Creates a new builder for {@link OutputGuardrailRequest}.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link OutputGuardrailRequest}.
     */
    public static class Builder {
        private ChatResponse responseFromLLM;
        private ChatExecutor chatExecutor;
        private GuardrailRequestParams requestParams;
        private ChatRequest chatRequest;

        private Builder() {}

        /**
         * Sets the response from the LLM.
         *
         * @param responseFromLLM the response from the LLM
         * @return this builder
         */
        public Builder responseFromLLM(ChatResponse responseFromLLM) {
            this.responseFromLLM = responseFromLLM;
            return this;
        }

        /**
         * Sets the chat executor.
         *
         * @param chatExecutor the chat executor
         * @return this builder
         */
        public Builder chatExecutor(ChatExecutor chatExecutor) {
            this.chatExecutor = chatExecutor;
            return this;
        }

        /**
         * Sets the common parameters.
         *
         * @param requestParams the common parameters
         * @return this builder
         */
        public Builder requestParams(GuardrailRequestParams requestParams) {
            this.requestParams = requestParams;
            return this;
        }

        /**
         * Sets the request that was sent to the model to produce the response.
         *
         * @param chatRequest the request, or {@code null} if it is not known
         * @return this builder
         * @since 1.23.0
         */
        @Experimental
        public Builder chatRequest(ChatRequest chatRequest) {
            this.chatRequest = chatRequest;
            return this;
        }

        /**
         * Builds a new {@link OutputGuardrailRequest}.
         *
         * @return a new {@link OutputGuardrailRequest}
         */
        public OutputGuardrailRequest build() {
            return new OutputGuardrailRequest(this);
        }
    }
}
