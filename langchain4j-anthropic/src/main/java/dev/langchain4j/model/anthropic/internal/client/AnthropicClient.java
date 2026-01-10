package dev.langchain4j.model.anthropic.internal.client;

import dev.langchain4j.Internal;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCountTokensRequest;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageRequest;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageResponse;
import dev.langchain4j.model.anthropic.internal.api.AnthropicModelsListResponse;
import dev.langchain4j.model.anthropic.internal.api.MessageTokenCountResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.spi.ServiceHelper;
import java.time.Duration;
import org.slf4j.Logger;

@Internal
public abstract class AnthropicClient {

    public abstract AnthropicCreateMessageResponse createMessage(AnthropicCreateMessageRequest request);

    public ParsedAndRawResponse createMessageWithRawResponse(AnthropicCreateMessageRequest request) {
        AnthropicCreateMessageResponse parsedResponse = createMessage(request);
        return new ParsedAndRawResponse(parsedResponse, null);
    }

    /**
     * @since 1.2.0
     */
    public void createMessage(
            AnthropicCreateMessageRequest request,
            AnthropicCreateMessageOptions options,
            StreamingChatResponseHandler handler) {
        createMessage(request, handler);
    }

    public abstract void createMessage(AnthropicCreateMessageRequest request, StreamingChatResponseHandler handler);

    public MessageTokenCountResponse countTokens(AnthropicCountTokensRequest request) {
        throw new UnsupportedOperationException("Token counting is not implemented");
    }

    public AnthropicModelsListResponse listModels() {
        throw new UnsupportedOperationException("Model listing is not supported by this client implementation");
    }

    @SuppressWarnings("rawtypes")
    public static AnthropicClient.Builder builder() {
        for (AnthropicClientBuilderFactory factory : ServiceHelper.loadFactories(AnthropicClientBuilderFactory.class)) {
            return factory.get();
        }
        // fallback to the default
        return DefaultAnthropicClient.builder();
    }

    /**
     * Builder class for constructing {@link AnthropicClient} instances.
     *
     * <p>Provides a fluent API for configuring HTTP client settings, authentication,
     * API versioning, and logging options. Uses the curiously recurring template pattern (CRTP)
     * to ensure method chaining returns the correct builder subtype.</p>
     *
     * <h2>Required Configuration</h2>
     * <ul>
     *   <li>{@link #baseUrl(String)} - The Anthropic API base URL</li>
     *   <li>{@link #apiKey(String)} - The API key for authentication</li>
     *   <li>{@link #version(String)} - The API version (e.g., "2023-06-01")</li>
     * </ul>
     *
     * <h2>Optional Configuration</h2>
     * <ul>
     *   <li>{@link #httpClientBuilder(HttpClientBuilder)} - Custom HTTP client builder</li>
     *   <li>{@link #timeout(Duration)} - Connection and read timeout (defaults: 15s connect, 60s read)</li>
     *   <li>{@link #beta(String)} - Beta features header for experimental capabilities</li>
     *   <li>{@link #logRequests(Boolean)} - Enable raw request logging</li>
     *   <li>{@link #logResponses(Boolean)} - Enable raw response logging</li>
     *   <li>{@link #logger(Logger)} - Custom logger instance</li>
     * </ul>
     *
     * <h2>Usage Example</h2>
     * <pre>{@code
     * AnthropicClient client = DefaultAnthropicClient.builder()
     *     .baseUrl("https://api.anthropic.com/v1")
     *     .apiKey("your-api-key")
     *     .version("2023-06-01")
     *     .timeout(Duration.ofSeconds(30))
     *     .logRequests(true)
     *     .logResponses(true)
     *     .build();
     * }</pre>
     *
     * @param <T> the type of {@link AnthropicClient} being built
     * @param <B> the concrete builder type (for method chaining)
     * @see AnthropicClient
     * @see DefaultAnthropicClient.Builder
     */
    public abstract static class Builder<T extends AnthropicClient, B extends Builder<T, B>> {

        public HttpClientBuilder httpClientBuilder;
        public String baseUrl;
        public String apiKey;
        public String version;
        public String beta;
        public Duration timeout;
        public Logger logger;
        public Boolean logRequests;
        public Boolean logResponses;

        /**
         * Builds and returns a new {@link AnthropicClient} instance.
         *
         * @return a configured {@link AnthropicClient} instance
         */
        public abstract T build();

        /**
         * Sets a custom HTTP client builder for configuring the underlying HTTP client.
         *
         * <p>If not specified, a default HTTP client builder is loaded via
         * {@link HttpClientBuilderLoader}.</p>
         *
         * @param httpClientBuilder the HTTP client builder to use
         * @return this builder for method chaining
         */
        public B httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return self();
        }

        /**
         * Sets the base URL for the Anthropic API.
         *
         * @param baseUrl the API base URL (e.g., "https://api.anthropic.com/v1")
         * @return this builder for method chaining
         */
        public B baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return self();
        }

        /**
         * Sets the API key for authentication.
         *
         * <p>The key is sent via the {@code x-api-key} header with every request.</p>
         *
         * @param apiKey the Anthropic API key
         * @return this builder for method chaining
         */
        public B apiKey(String apiKey) {
            this.apiKey = apiKey;
            return self();
        }

        /**
         * Sets the API version to use.
         *
         * <p>The version is sent via the {@code anthropic-version} header. Anthropic guarantees
         * backward compatibility within a version: existing input/output parameters are preserved,
         * though new optional inputs and output values may be added.</p>
         *
         * <p>Recommended version: "2023-06-01" (incremental streaming, named SSE events).</p>
         *
         * @param version the API version string (e.g., "2023-06-01")
         * @return this builder for method chaining
         * @see <a href="https://docs.anthropic.com/en/api/versioning">Anthropic API Versioning</a>
         */
        public B version(String version) {
            this.version = version;
            return self();
        }

        /**
         * Sets the beta features header for accessing experimental API capabilities.
         *
         * <p>Beta features allow access to new model capabilities before they are part of
         * the standard API. These features are subject to change and may be modified or
         * removed in future releases.</p>
         *
         * <p>The value is sent via the {@code anthropic-beta} header.</p>
         *
         * @param beta the beta feature identifier(s)
         * @return this builder for method chaining
         */
        public B beta(String beta) {
            this.beta = beta;
            return self();
        }

        /**
         * Sets the timeout duration for both connection and read operations.
         *
         * <p>If not specified, defaults are applied:</p>
         * <ul>
         *   <li>Connection timeout: 15 seconds</li>
         *   <li>Read timeout: 60 seconds</li>
         * </ul>
         *
         * @param timeout the timeout duration to apply to both connection and read operations
         * @return this builder for method chaining
         */
        public B timeout(Duration timeout) {
            this.timeout = timeout;
            return self();
        }

        /**
         * Enables logging of raw HTTP requests.
         *
         * <p>Convenience method equivalent to {@code logRequests(true)}.</p>
         *
         * @return this builder for method chaining
         * @see #logRequests(Boolean)
         */
        public B logRequests() {
            return logRequests(true);
        }

        /**
         * Sets whether to log raw HTTP requests.
         *
         * <p>When enabled, the full HTTP request (including headers and body) is logged
         * for debugging purposes. If {@code null} is provided, defaults to {@code false}.</p>
         *
         * @param logRequests {@code true} to enable request logging, {@code false} to disable,
         *                    or {@code null} (defaults to {@code false})
         * @return this builder for method chaining
         */
        public B logRequests(Boolean logRequests) {
            if (logRequests == null) {
                logRequests = false;
            }
            this.logRequests = logRequests;
            return self();
        }

        /**
         * Enables logging of raw HTTP responses.
         *
         * <p>Convenience method equivalent to {@code logResponses(true)}.</p>
         *
         * @return this builder for method chaining
         * @see #logResponses(Boolean)
         */
        public B logResponses() {
            return logResponses(true);
        }

        /**
         * Sets whether to log raw HTTP responses.
         *
         * <p>When enabled, the full HTTP response (including headers and body) is logged
         * for debugging purposes. If {@code null} is provided, defaults to {@code false}.</p>
         *
         * @param logResponses {@code true} to enable response logging, {@code false} to disable,
         *                     or {@code null} (defaults to {@code false})
         * @return this builder for method chaining
         */
        public B logResponses(Boolean logResponses) {
            if (logResponses == null) {
                logResponses = false;
            }
            this.logResponses = logResponses;
            return self();
        }

        /**
         * Sets a custom logger for request and response logging.
         *
         * <p>If not specified, the default LangChain4j logger is used.</p>
         *
         * @param logger an alternate {@link Logger} to use instead of the default
         * @return this builder for method chaining
         */
        public B logger(Logger logger) {
            this.logger = logger;
            return self();
        }

        /**
         * Returns this builder instance cast to the concrete builder type.
         *
         * <p>This method centralizes the unchecked cast required by the curiously recurring
         * template pattern (CRTP), ensuring type-safe method chaining in subclasses.</p>
         *
         * @return this builder cast to type {@code B}
         */
        @SuppressWarnings("unchecked")
        private B self() {
            return (B) this;
        }
    }
}
