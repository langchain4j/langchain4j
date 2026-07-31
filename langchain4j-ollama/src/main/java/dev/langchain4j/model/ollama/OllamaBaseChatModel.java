package dev.langchain4j.model.ollama;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.util.Arrays.asList;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.internal.ChatRequestValidationUtils;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;

abstract class OllamaBaseChatModel {

    protected OllamaClient client;
    protected OllamaChatRequestParameters defaultRequestParameters;
    protected boolean returnThinking;
    protected List<ChatModelListener> listeners;
    protected Set<Capability> supportedCapabilities;

    void init(Builder<? extends OllamaBaseChatModel, ? extends Builder<?, ?>> builder) {
        this.client = OllamaClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(builder.baseUrl)
                .timeout(builder.timeout)
                .customHeaders(builder.customHeadersSupplier)
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .logger(builder.logger)
                .build();

        ChatRequestParameters commonParameters;
        if (builder.defaultRequestParameters != null) {
            validate(builder.defaultRequestParameters);
            commonParameters = builder.defaultRequestParameters;
        } else {
            commonParameters = DefaultChatRequestParameters.EMPTY;
        }

        OllamaChatRequestParameters ollamaParameters =
                builder.defaultRequestParameters instanceof OllamaChatRequestParameters ollamaChatRequestParameters
                        ? ollamaChatRequestParameters
                        : OllamaChatRequestParameters.EMPTY;

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
                .think(getOrDefault(builder.think, ollamaParameters.think()))
                .numThread(ollamaParameters.numThread())
                .numKeep(ollamaParameters.numKeep())
                .typicalP(ollamaParameters.typicalP())
                .numBatch(ollamaParameters.numBatch())
                .numGPU(ollamaParameters.numGPU())
                .mainGPU(ollamaParameters.mainGPU())
                .useMmap(ollamaParameters.useMmap())
                .build();
        this.returnThinking = getOrDefault(builder.returnThinking, false);
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
        protected Boolean think;
        protected Boolean returnThinking;
        protected Duration timeout;
        protected Supplier<Map<String, String>> customHeadersSupplier;
        protected Boolean logRequests;
        protected Boolean logResponses;
        protected Logger logger;
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

        /**
         * Sets the base URL of the Ollama server.
         * <p>
         * Defaults to {@code http://localhost:11434}.
         *
         * @param baseUrl the Ollama server base URL
         * @return {@code this}
         */
        public B baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return self();
        }

        /**
         * Sets default {@link ChatRequestParameters} that are merged into every request.
         * Individual request parameters take precedence over these defaults.
         *
         * @param defaultRequestParameters the default request parameters
         * @return {@code this}
         */
        public B defaultRequestParameters(ChatRequestParameters defaultRequestParameters) {
            this.defaultRequestParameters = defaultRequestParameters;
            return self();
        }

        /**
         * Sets the Ollama model name to use for chat completions.
         * <p>
         * The model must be available on the Ollama server (run {@code ollama pull <model>} first).
         * Examples: {@code "llama3"}, {@code "mistral"}, {@code "deepseek-r1"}.
         *
         * @param modelName the model name
         * @return {@code this}
         */
        public B modelName(String modelName) {
            this.modelName = modelName;
            return self();
        }

        /**
         * Sets the sampling temperature in the range {@code [0.0, 1.0]}.
         * Higher values produce more creative output; lower values produce more deterministic output.
         *
         * @param temperature the sampling temperature
         * @return {@code this}
         */
        public B temperature(Double temperature) {
            this.temperature = temperature;
            return self();
        }

        /**
         * Sets the top-K sampling value. Only the {@code topK} most-likely next tokens are considered at each step.
         *
         * @param topK the number of top tokens to sample from
         * @return {@code this}
         */
        public B topK(Integer topK) {
            this.topK = topK;
            return self();
        }

        /**
         * Sets the nucleus sampling probability (top-p).
         * Only the tokens whose cumulative probability exceeds this threshold are considered.
         *
         * @param topP the nucleus sampling threshold
         * @return {@code this}
         */
        public B topP(Double topP) {
            this.topP = topP;
            return self();
        }

        /**
         * Sets the Mirostat sampling algorithm version.
         * <ul>
         *   <li>{@code 0} — disabled (default)</li>
         *   <li>{@code 1} — Mirostat v1</li>
         *   <li>{@code 2} — Mirostat v2</li>
         * </ul>
         * Mirostat aims to maintain a consistent level of perplexity in the output.
         * See the <a href="https://arxiv.org/abs/2007.14966">Mirostat paper</a>.
         *
         * @param mirostat the Mirostat algorithm version
         * @return {@code this}
         */
        public B mirostat(Integer mirostat) {
            this.mirostat = mirostat;
            return self();
        }

        /**
         * Sets the Mirostat learning rate (eta), which controls how quickly the algorithm
         * adjusts to feedback. A lower value results in slower adjustments.
         * <p>
         * Only applicable when {@link #mirostat(Integer)} is set to 1 or 2.
         *
         * @param mirostatEta the Mirostat learning rate
         * @return {@code this}
         */
        public B mirostatEta(Double mirostatEta) {
            this.mirostatEta = mirostatEta;
            return self();
        }

        /**
         * Sets the Mirostat target entropy (tau), which controls the balance between
         * coherence and diversity in the output. A lower value results in more focused text.
         * <p>
         * Only applicable when {@link #mirostat(Integer)} is set to 1 or 2.
         *
         * @param mirostatTau the Mirostat target entropy
         * @return {@code this}
         */
        public B mirostatTau(Double mirostatTau) {
            this.mirostatTau = mirostatTau;
            return self();
        }

        /**
         * Sets the number of tokens to look back when applying the repeat penalty.
         * Use {@code -1} to use the full context window.
         *
         * @param repeatLastN the lookback window size in tokens
         * @return {@code this}
         */
        public B repeatLastN(Integer repeatLastN) {
            this.repeatLastN = repeatLastN;
            return self();
        }

        /**
         * Sets the penalty applied to tokens that have already appeared in the context window,
         * discouraging repetition. Values greater than {@code 1.0} penalise repetition more strongly.
         *
         * @param repeatPenalty the repeat penalty factor
         * @return {@code this}
         */
        public B repeatPenalty(Double repeatPenalty) {
            this.repeatPenalty = repeatPenalty;
            return self();
        }

        /**
         * Sets the random seed for deterministic output.
         * Setting the same seed and prompt will produce the same response.
         *
         * @param seed the random seed
         * @return {@code this}
         */
        public B seed(Integer seed) {
            this.seed = seed;
            return self();
        }

        /**
         * Sets the maximum number of tokens to generate.
         * Use {@code -1} for unlimited (model's context window limit).
         *
         * @param numPredict the maximum number of tokens to generate
         * @return {@code this}
         */
        public B numPredict(Integer numPredict) {
            this.numPredict = numPredict;
            return self();
        }

        /**
         * Sets the context window size in tokens — the number of tokens the model can
         * attend to when generating a response.
         * <p>
         * Larger values consume more memory. Defaults to the model's built-in context size.
         *
         * @param numCtx the context window size
         * @return {@code this}
         */
        public B numCtx(Integer numCtx) {
            this.numCtx = numCtx;
            return self();
        }

        /**
         * Sets sequences that, when generated, will cause the model to stop generating further tokens.
         *
         * @param stop the list of stop sequences
         * @return {@code this}
         */
        public B stop(List<String> stop) {
            this.stop = stop;
            return self();
        }

        /**
         * Sets the minimum probability threshold for token selection.
         * Tokens with probability below {@code minP * (probability of the most likely token)} are filtered out.
         * This is an alternative to {@link #topP(Double)} nucleus sampling.
         *
         * @param minP the minimum probability threshold
         * @return {@code this}
         */
        public B minP(Double minP) {
            this.minP = minP;
            return self();
        }

        /**
         * Sets the response format, enabling structured output such as JSON mode.
         *
         * @param responseFormat the desired response format
         * @return {@code this}
         */
        public B responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return self();
        }

        /**
         * Controls <a href="https://ollama.com/blog/thinking">thinking</a>.
         * <pre>
         * <code>true</code>: the LLM thinks and returns thoughts in a separate <code>thinking</code> field
         * <code>false</code>: the LLM does not think
         * <code>null</code> (not set): reasoning LLMs (e.g., DeepSeek R1) will prepend thoughts, delimited by </code>&lt;think&gt;</code> and </code>&lt;/think&gt;</code>, to the actual response
         * </pre>
         *
         * @see #returnThinking(Boolean)
         */
        public B think(Boolean think) {
            this.think = think;
            return self();
        }

        /**
         * Controls whether to return thinking/reasoning text (if available) inside {@link AiMessage#thinking()}
         * and whether to invoke the {@link StreamingChatResponseHandler#onPartialThinking(PartialThinking)} callback.
         * Please note that this does not enable thinking/reasoning for the LLM;
         * it only controls whether to parse the {@code thinking} field from the API response
         * and return it inside the {@link AiMessage}.
         * <p>
         * Disabled by default.
         * If enabled, the thinking text will be stored within the {@link AiMessage} and may be persisted.
         *
         * @see #think(Boolean)
         */
        public B returnThinking(Boolean returnThinking) {
            this.returnThinking = returnThinking;
            return self();
        }

        /**
         * Sets the HTTP request timeout for calls to the Ollama server.
         * <p>
         * NOTE: This overrides any timeout set via {@link #httpClientBuilder(HttpClientBuilder)}.
         *
         * @param timeout the request timeout
         * @return {@code this}
         */
        public B timeout(Duration timeout) {
            this.timeout = timeout;
            return self();
        }

        /**
         * Sets custom HTTP headers.
         */
        public B customHeaders(Map<String, String> customHeaders) {
            this.customHeadersSupplier = () -> customHeaders;
            return self();
        }

        /**
         * Sets a supplier for custom HTTP headers.
         * The supplier is called before each request, allowing dynamic header values.
         * For example, this is useful for OAuth2 tokens that expire and need refreshing.
         */
        public B customHeaders(Supplier<Map<String, String>> customHeadersSupplier) {
            this.customHeadersSupplier = customHeadersSupplier;
            return self();
        }

        /**
         * Enables debug logging of HTTP request bodies sent to the Ollama server.
         *
         * @param logRequests whether to log requests
         * @return {@code this}
         */
        public B logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return self();
        }

        /**
         * Enables debug logging of HTTP response bodies received from the Ollama server.
         *
         * @param logResponses whether to log responses
         * @return {@code this}
         */
        public B logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return self();
        }

        /**
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for logging requests and responses.
         * @return {@code this}.
         */
        public B logger(Logger logger) {
            this.logger = logger;
            return self();
        }

        /**
         * Sets the list of {@link ChatModelListener}s to be notified on each request and response.
         * Useful for logging, metrics, and observability integrations.
         *
         * @param listeners the chat model listeners
         * @return {@code this}
         */
        public B listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return self();
        }

        /**
         * Declares the capabilities supported by the model.
         * This influences how LangChain4j generates requests for this model.
         *
         * @param supportedCapabilities the set of capabilities to declare
         * @return {@code this}
         */
        public B supportedCapabilities(Set<Capability> supportedCapabilities) {
            this.supportedCapabilities = supportedCapabilities;
            return self();
        }

        /**
         * Declares the capabilities supported by the model.
         *
         * @param supportedCapabilities the capabilities to declare
         * @return {@code this}
         */
        public B supportedCapabilities(Capability... supportedCapabilities) {
            return supportedCapabilities(new HashSet<>(asList(supportedCapabilities)));
        }

        public abstract C build();
    }
}
