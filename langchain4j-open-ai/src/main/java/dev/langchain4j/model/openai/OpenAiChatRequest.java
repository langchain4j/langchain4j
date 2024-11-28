package dev.langchain4j.model.openai;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ChatRequest;

import java.util.Map;

import static dev.langchain4j.internal.Utils.copyIfNotNull;

@Experimental
public class OpenAiChatRequest extends ChatRequest {

    private final Map<String, Integer> logitBias;
    private final Integer seed;
    // TODO parallel_tool_calls
    // TODO service tier
    // TODO user
    // TODO store, metadata
    // TODO logprobs, top_logprobs?
    // TODO max_completion_tokens?

    protected OpenAiChatRequest(Builder builder) { // TODO
        super(builder);
        this.logitBias = copyIfNotNull(builder.logitBias);
        this.seed = builder.seed;
    }

    OpenAiChatRequest(ChatRequest chatRequest) { // TODO
        super(chatRequest.toBuilder());
        if (chatRequest instanceof OpenAiChatRequest openAiChatRequest) {
            this.logitBias = copyIfNotNull(openAiChatRequest.logitBias);
            this.seed = openAiChatRequest.seed;
        } else {
            this.logitBias = null;
            this.seed = null;
        }
    }

    public Map<String, Integer> logitBias() {
        return logitBias;
    }

    public Integer seed() {
        return seed;
    }

    // TODO eq, hash, tostr

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ChatRequest.Builder<Builder> {

        private Map<String, Integer> logitBias;
        private Integer seed;

        public Builder logitBias(Map<String, Integer> logitBias) {
            this.logitBias = logitBias;
            return this;
        }

        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public OpenAiChatRequest build() {
            return new OpenAiChatRequest(this);
        }
    }
}
