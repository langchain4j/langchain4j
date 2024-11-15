package dev.langchain4j.model.watsonx.internal.api;


import java.time.Duration;

public abstract class WatsonxAiClient {


    protected WatsonxAiClient() {
    }

    public abstract WatsonxAiChatCompletionResponse chatCompletion(WatsonxChatCompletionRequest request, String version);


    public abstract static class Builder<T extends WatsonxAiClient, B extends Builder<T, B>> {
        public String baseUrl;
        public String token;
        public Duration timeout;
        public boolean logRequests;
        public boolean logResponses;

        public abstract T build();

        public B baseUrl(String baseUrl) {
            if (baseUrl == null || baseUrl.trim().isEmpty()) {
                throw new IllegalArgumentException("baseUrl cannot be null or empty");
            }
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
            return (B) this;
        }

        public B token(String token) {
            if (token == null || token.trim().isEmpty()) {
                throw new IllegalArgumentException("MistralAI API Key must be defined. It can be generated here: https://console.mistral.ai/user/api-keys");
            }
            this.token = token;
            return (B) this;
        }

        public B timeout(Duration timeout) {
            if (timeout == null) {
                throw new IllegalArgumentException("callTimeout cannot be null");
            }
            this.timeout = timeout;
            return (B) this;
        }

        public B logRequests() {
            return logRequests(true);
        }

        public B logRequests(Boolean logRequests) {
            if (logRequests == null) {
                logRequests = false;
            }
            this.logRequests = logRequests;
            return (B) this;
        }

        public B logResponses() {
            return logResponses(true);
        }

        public B logResponses(Boolean logResponses) {
            if (logResponses == null) {
                logResponses = false;
            }
            this.logResponses = logResponses;
            return (B) this;
        }
    }

}
