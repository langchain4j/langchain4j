package dev.langchain4j.model.ovhai.internal.client;

import dev.langchain4j.spi.ServiceHelper;
import java.time.Duration;

public abstract class OvhAiClient {

    @SuppressWarnings("rawtypes")
    public static OvhAiClient.Builder builder() {
        for (OvhAiClientBuilderFactory factory : ServiceHelper.loadFactories(OvhAiClientBuilderFactory.class)) {
            return factory.get();
        }
        // fallback to the default
        return DefaultOvhAiClient.builder();
    }

    public abstract static class Builder<T extends OvhAiClient, B extends Builder<T, B>> {

        public String baseUrl;
        public String apiKey;
        public Duration timeout;
        public Boolean logRequests;
        public Boolean logResponses;

        public abstract T build();

        public B baseUrl(String baseUrl) {
            if ((baseUrl == null) || baseUrl.trim().isEmpty()) {
                throw new IllegalArgumentException("baseUrl cannot be null or empty");
            }
            this.baseUrl = baseUrl;
            return (B) this;
        }

        public B apiKey(String apiKey) {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new IllegalArgumentException(
                    "OVHcloud API key must be defined. " +
                    "It can be generated here: https://endpoints.ai.cloud.ovh.net/"
                );
            }
            this.apiKey = apiKey;
            return (B) this;
        }

        public B timeout(Duration timeout) {
            if (timeout == null) {
                throw new IllegalArgumentException("timeout cannot be null");
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
