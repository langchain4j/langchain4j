package dev.langchain4j.model.ollama;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.util.Arrays.asList;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.internal.ChatRequestValidationUtils;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

abstract class OllamaBaseChatModel {

    protected OllamaClient client;
    protected OllamaChatRequestParameters defaultRequestParameters;
    protected List<ChatModelListener> listeners;
    protected Set<Capability> supportedCapabilities;

    void init(Builder<? extends OllamaBaseChatModel, ? extends Builder<?, ?>> builder) {
        this.client = OllamaClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(builder.baseUrl)
                .timeout(builder.timeout)
                .customHeaders(builder.customHeaders)
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .build();

        ChatRequestParameters commonParameters;
        if (builder.defaultRequestParameters != null) {
            validate(builder.defaultRequestParameters);
            commonParameters = builder.defaultRequestParameters;
        } else {
            commonParameters = DefaultChatRequestParameters.EMPTY;
        }

        OllamaChatRequestParameters ollamaParameters =
                builder.defaultRequestParameters instanceof OllamaChatRequestParameters ollamaChatRequestParameters ?
                        ollamaChatRequestParameters :
                        OllamaChatRequestParameters.EMPTY;

        this.defaultRequestParameters = OllamaChatRequestParameters.builder()
                // common parameters
                .modelName(getOrDefault(builder.modelName, commonParameters.modelName()))
                .temperature(getOrDefault(builder.temperature, commonParameters.temperature()))
                .topP(getOrDefault(builder.topP, commonParameters.topP()))
                .topK(getOrDefault(builder.topK, commonParameters.topK()))
                .maxOutputTokens(getOrDefault(builder.numPredict, commonParameters.maxOutputTokens()))
                .stopSequences(getOrDefault(builder.stop, commonParameters.stopSequences()))
                .toolSpecifications(commonParameters.toolSpecifications())
                .responseFormat(getOrDefault(builder.responseFormat, commonParameters.responseFormat()))
                // Ollama-specific parameters
                .mirostat(getOrDefault(builder.mirostat, ollamaParameters.mirostat()))
                .mirostatEta(getOrDefault(builder.mirostatEta, ollamaParameters.mirostatEta()))
                .mirostatTau(getOrDefault(builder.mirostatTau, ollamaParameters.mirostatTau()))
                .numCtx(getOrDefault(builder.numCtx, ollamaParameters.numCtx()))
                .repeatLastN(getOrDefault(builder.repeatLastN, ollamaParameters.repeatLastN()))
                .repeatPenalty(getOrDefault(builder.repeatPenalty, ollamaParameters.repeatPenalty()))
                .seed(getOrDefault(builder.seed, ollamaParameters.seed()))
                .minP(getOrDefault(builder.minP, ollamaParameters.minP()))
                .keepAlive(ollamaParameters.keepAlive())
                .build();

        this.listeners = copy(builder.listeners);
        this.supportedCapabilities = copy(builder.supportedCapabilities);
    }

    protected void validate(ChatRequestParameters chatRequestParameters) {
        InternalOllamaHelper.validate(chatRequestParameters);
        ChatRequestValidationUtils.validate(chatRequestParameters.toolChoice());
    }

    protected abstract static class Builder<C extends OllamaBaseChatModel, B extends Builder<C, B>> {

        protected HttpClientBuilder httpClientBuilder;
        protected String baseUrl;

        protected ChatRequestParameters defaultRequestParameters;
        protected String modelName;
        protected Double temperature;
        protected Integer topK;
        protected Double topP;
        protected Integer mirostat;
        protected Double mirostatEta;
        protected Double mirostatTau;
        protected Integer numCtx;
        protected Integer repeatLastN;
        protected Double repeatPenalty;
        protected Integer seed;
        protected Integer numPredict;
        protected List<String> stop;
        protected Double minP;
        protected ResponseFormat responseFormat;
        protected Duration timeout;
        protected Map<String, String> customHeaders;
        protected Boolean logRequests;
        protected Boolean logResponses;
        protected List<ChatModelListener> listeners;
        protected Set<Capability> supportedCapabilities;

        protected B self() {
            return (B) this;
        }

        /**
         * Sets the {@link HttpClientBuilder} that will be used to create the {@link HttpClient}
         * that will be used to communicate with Ollama.
         * <p>
         * NOTE: {@link #timeout(Duration)} overrides timeouts set on the {@link HttpClientBuilder}.
         */
        public B httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return self();
        }

        public B baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return self();
        }

        public B defaultRequestParameters(ChatRequestParameters defaultRequestParameters) {
            this.defaultRequestParameters = defaultRequestParameters;
            return self();
        }

        public B modelName(String modelName) {
            this.modelName = modelName;
            return self();
        }

        public B temperature(Double temperature) {
            this.temperature = temperature;
            return self();
        }

        public B topK(Integer topK) {
            this.topK = topK;
            return self();
        }

        public B topP(Double topP) {
            this.topP = topP;
            return self();
        }

        public B mirostat(Integer mirostat) {
            this.mirostat = mirostat;
            return self();
        }

        public B mirostatEta(Double mirostatEta) {
            this.mirostatEta = mirostatEta;
            return self();
        }

        public B mirostatTau(Double mirostatTau) {
            this.mirostatTau = mirostatTau;
            return self();
        }

        public B repeatLastN(Integer repeatLastN) {
            this.repeatLastN = repeatLastN;
            return self();
        }

        public B repeatPenalty(Double repeatPenalty) {
            this.repeatPenalty = repeatPenalty;
            return self();
        }

        public B seed(Integer seed) {
            this.seed = seed;
            return self();
        }

        public B numPredict(Integer numPredict) {
            this.numPredict = numPredict;
            return self();
        }

        public B numCtx(Integer numCtx) {
            this.numCtx = numCtx;
            return self();
        }

        public B stop(List<String> stop) {
            this.stop = stop;
            return self();
        }

        public B minP(Double minP) {
            this.minP = minP;
            return self();
        }

        public B responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return self();
        }

        public B timeout(Duration timeout) {
            this.timeout = timeout;
            return self();
        }

        public B customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return self();
        }

        public B logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return self();
        }

        public B logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return self();
        }

        public B listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return self();
        }

        public B supportedCapabilities(Set<Capability> supportedCapabilities) {
            this.supportedCapabilities = supportedCapabilities;
            return self();
        }

        public B supportedCapabilities(Capability... supportedCapabilities) {
            return supportedCapabilities(new HashSet<>(asList(supportedCapabilities)));
        }

        public abstract C build();
    }
}
