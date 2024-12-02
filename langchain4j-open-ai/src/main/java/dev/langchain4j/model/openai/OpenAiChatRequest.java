package dev.langchain4j.model.openai;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ChatRequest;

import java.util.Map;

import static dev.langchain4j.internal.Utils.copyIfNotNull;

@Experimental
public class OpenAiChatRequest extends ChatRequest {

    private final Map<String, Integer> logitBias;
    private final Boolean parallelToolCalls;
    private final Integer seed;
    private final String user;
    private final Boolean store;
    private final Map<String, String> metadata;
    private final String serviceTier; // TODO or enum?
    // TODO max_completion_tokens?

    protected OpenAiChatRequest(Builder builder) { // TODO
        super(builder);
        this.logitBias = copyIfNotNull(builder.logitBias);
        this.parallelToolCalls = builder.parallelToolCalls;
        this.seed = builder.seed;
        this.user = builder.user;
        this.store = builder.store;
        this.metadata = copyIfNotNull(builder.metadata);
        this.serviceTier = builder.serviceTier;
    }

    OpenAiChatRequest(ChatRequest chatRequest) { // TODO
        super(chatRequest.toBuilder());
        if (chatRequest instanceof OpenAiChatRequest openAiChatRequest) {
            this.logitBias = copyIfNotNull(openAiChatRequest.logitBias);
            this.parallelToolCalls = openAiChatRequest.parallelToolCalls;
            this.seed = openAiChatRequest.seed;
            this.user = openAiChatRequest.user;
            this.store = openAiChatRequest.store;
            this.metadata = openAiChatRequest.metadata;
            this.serviceTier = openAiChatRequest.serviceTier;
        } else {
            this.logitBias = null;
            this.parallelToolCalls = null;
            this.seed = null;
            this.user = null;
            this.store = null;
            this.metadata = null;
            this.serviceTier = null;
        }
    }

    public Map<String, Integer> logitBias() {
        return logitBias;
    }

    public Boolean parallelToolCalls() {
        return parallelToolCalls;
    }

    public Integer seed() {
        return seed;
    }

    public String user() {
        return user;
    }

    public Boolean store() {
        return store;
    }

    public Map<String, String> metadata() {
        return metadata;
    }

    public String serviceTier() {
        return serviceTier;
    }

    // TODO eq, hash, tostr

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ChatRequest.Builder<Builder> {

        private Map<String, Integer> logitBias;
        private Boolean parallelToolCalls;
        private Integer seed;
        private String user;
        private Boolean store;
        private Map<String, String> metadata;
        private String serviceTier;

        public Builder logitBias(Map<String, Integer> logitBias) {
            this.logitBias = logitBias;
            return this;
        }

        public Builder parallelToolCalls(Boolean parallelToolCalls) {
            this.parallelToolCalls = parallelToolCalls;
            return this;
        }

        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder store(Boolean store) {
            this.store = store;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder serviceTier(String serviceTier) {
            this.serviceTier = serviceTier;
            return this;
        }

        public OpenAiChatRequest build() {
            return new OpenAiChatRequest(this);
        }
    }
}
