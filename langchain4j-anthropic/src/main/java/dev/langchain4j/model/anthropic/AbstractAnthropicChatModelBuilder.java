package dev.langchain4j.model.anthropic;

import static java.util.Arrays.asList;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;

/**
 * Abstract base builder containing common configuration options for Anthropic chat models.
 *
 * <p>This class serves as a foundation for building specific Anthropic chat model builders, providing
 * shared properties and methods for configuring various aspects of chat model requests.</p>
 *
 * @param <T> the concrete builder type for method chaining
 *
 * <p>Common configuration options include:</p>
 * <ul>
 *     <li><strong>httpClientBuilder:</strong> Builder for configuring HTTP client settings.</li>
 *     <li><strong>baseUrl:</strong> The base URL for the Anthropic API (defaults to https://api.anthropic.com/v1/).</li>
 *     <li><strong>apiKey:</strong> The API key used for authentication with the Anthropic API.</li>
 *     <li><strong>version:</strong> The Anthropic API version to use (defaults to "2023-06-01").</li>
 *     <li><strong>beta:</strong> Beta feature flags to enable (e.g., "prompt-caching-2024-07-31").</li>
 *     <li><strong>modelName:</strong> The name of the Claude model to use.</li>
 *     <li><strong>temperature:</strong> Controls the randomness of the output; higher values produce more random results.</li>
 *     <li><strong>topP:</strong> The maximum cumulative probability of tokens to consider when sampling.</li>
 *     <li><strong>topK:</strong> The number of most likely tokens to consider when sampling.</li>
 *     <li><strong>maxTokens:</strong> The maximum number of tokens to generate in the response.</li>
 *     <li><strong>stopSequences:</strong> A list of sequences that will stop output generation when encountered.</li>
 *     <li><strong>responseFormat:</strong> Specifies the format of the response from the chat model.</li>
 *     <li><strong>toolSpecifications:</strong> A list of tools that can be used in the chat model requests.</li>
 *     <li><strong>toolChoice:</strong> Controls how the model selects tools to use.</li>
 *     <li><strong>serverTools:</strong> Server-side tools like web_search or code_execution.</li>
 *     <li><strong>cacheSystemMessages:</strong> Enables caching of system messages for cost optimization.</li>
 *     <li><strong>cacheTools:</strong> Enables caching of tool definitions for cost optimization.</li>
 *     <li><strong>thinkingType:</strong> Enables extended thinking/reasoning capabilities.</li>
 *     <li><strong>thinkingBudgetTokens:</strong> Token budget for extended thinking.</li>
 *     <li><strong>timeout:</strong> Duration to wait for a response before timing out.</li>
 *     <li><strong>logRequests:</strong> Flag to enable logging of requests made to the chat model.</li>
 *     <li><strong>logResponses:</strong> Flag to enable logging of responses received from the chat model.</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * AnthropicChatModel model = AnthropicChatModel.builder()
 *     .apiKey(System.getenv("ANTHROPIC_API_KEY"))
 *     .modelName("claude-sonnet-4-5")
 *     .temperature(0.7)
 *     .maxTokens(1024)
 *     .logRequests(true)
 *     .logResponses(true)
 *     .build();
 * }</pre>
 */
@Internal
public abstract class AbstractAnthropicChatModelBuilder<T extends AbstractAnthropicChatModelBuilder<T>> {

    protected HttpClientBuilder httpClientBuilder;
    protected String baseUrl;
    protected String apiKey;
    protected String version;
    protected String beta;
    protected String modelName;
    protected Double temperature;
    protected Double topP;
    protected Integer topK;
    protected Integer maxTokens;
    protected List<String> stopSequences;
    protected ResponseFormat responseFormat;
    protected List<ToolSpecification> toolSpecifications;
    protected ToolChoice toolChoice;
    protected String toolChoiceName;
    protected Boolean disableParallelToolUse;
    protected List<AnthropicServerTool> serverTools;
    protected Boolean returnServerToolResults;
    protected Set<String> toolMetadataKeysToSend;
    protected Boolean cacheSystemMessages;
    protected Boolean cacheTools;
    protected String thinkingType;
    protected Integer thinkingBudgetTokens;
    protected Boolean returnThinking;
    protected Boolean sendThinking;
    protected Duration timeout;
    protected Boolean logRequests;
    protected Boolean logResponses;
    protected Logger logger;
    protected ChatRequestParameters defaultRequestParameters;
    protected String userId;
    protected Map<String, Object> customParameters;
    protected Boolean strictTools;

    @SuppressWarnings("unchecked")
    protected T self() {
        return (T) this;
    }

    /**
     * Sets the HTTP client builder for configuring the underlying HTTP client.
     *
     * @param httpClientBuilder the HTTP client builder
     * @return {@code this}
     */
    public T httpClientBuilder(HttpClientBuilder httpClientBuilder) {
        this.httpClientBuilder = httpClientBuilder;
        return self();
    }

    /**
     * Sets the base URL for the Anthropic API.
     *
     * <p>Defaults to "https://api.anthropic.com/v1/" if not specified.</p>
     *
     * @param baseUrl the base URL
     * @return {@code this}
     */
    public T baseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return self();
    }

    /**
     * Sets the API key for authentication with the Anthropic API.
     *
     * @param apiKey the API key
     * @return {@code this}
     */
    public T apiKey(String apiKey) {
        this.apiKey = apiKey;
        return self();
    }

    /**
     * Sets the Anthropic API version to use.
     *
     * <p>Defaults to "2023-06-01" if not specified.</p>
     *
     * @param version the API version string
     * @return {@code this}
     */
    public T version(String version) {
        this.version = version;
        return self();
    }

    /**
     * Sets beta feature flags to enable experimental features.
     *
     * <p>Example: "prompt-caching-2024-07-31" for prompt caching support.</p>
     *
     * @param beta the beta feature flag
     * @return {@code this}
     */
    public T beta(String beta) {
        this.beta = beta;
        return self();
    }

    /**
     * Sets the Claude model name to use.
     *
     * @param modelName the model name (e.g., "claude-sonnet-4-5", "claude-opus-4-5")
     * @return {@code this}
     */
    public T modelName(String modelName) {
        this.modelName = modelName;
        return self();
    }

    /**
     * Sets the Claude model name to use from the enum.
     *
     * @param modelName the model name enum value
     * @return {@code this}
     */
    public T modelName(AnthropicChatModelName modelName) {
        this.modelName = modelName.toString();
        return self();
    }

    /**
     * Controls the randomness of the output.
     *
     * <p>Higher values (e.g., 1.0) produce more random results, while lower values (e.g., 0.0)
     * produce more deterministic results.</p>
     *
     * @param temperature the temperature value (typically between 0.0 and 1.0)
     * @return {@code this}
     */
    public T temperature(Double temperature) {
        this.temperature = temperature;
        return self();
    }

    /**
     * Sets the maximum cumulative probability of tokens to consider when sampling (nucleus sampling).
     *
     * <p>For example, 0.9 means only the tokens comprising the top 90% probability mass are considered.</p>
     *
     * @param topP the top-p value (between 0.0 and 1.0)
     * @return {@code this}
     */
    public T topP(Double topP) {
        this.topP = topP;
        return self();
    }

    /**
     * Sets the number of most likely tokens to consider when sampling (top-k sampling).
     *
     * @param topK the number of tokens to consider
     * @return {@code this}
     */
    public T topK(Integer topK) {
        this.topK = topK;
        return self();
    }

    /**
     * Sets the maximum number of tokens to generate in the response.
     *
     * <p>Defaults to 1024 if not specified.</p>
     *
     * @param maxTokens the maximum number of output tokens
     * @return {@code this}
     */
    public T maxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
        return self();
    }

    /**
     * Sets sequences that will stop output generation when encountered.
     *
     * @param stopSequences the list of stop sequences
     * @return {@code this}
     */
    public T stopSequences(List<String> stopSequences) {
        this.stopSequences = stopSequences;
        return self();
    }

    /**
     * Sets the response format for structured output.
     *
     * @param responseFormat the response format specification
     * @return {@code this}
     */
    public T responseFormat(ResponseFormat responseFormat) {
        this.responseFormat = responseFormat;
        return self();
    }

    /**
     * Sets the tool specifications available for the model to use.
     *
     * @param toolSpecifications the list of tool specifications
     * @return {@code this}
     */
    public T toolSpecifications(List<ToolSpecification> toolSpecifications) {
        this.toolSpecifications = toolSpecifications;
        return self();
    }

    /**
     * Sets the tool specifications available for the model to use.
     *
     * @param toolSpecifications the tool specifications
     * @return {@code this}
     */
    public T toolSpecifications(ToolSpecification... toolSpecifications) {
        return toolSpecifications(asList(toolSpecifications));
    }

    /**
     * Controls how the model selects tools to use.
     *
     * @param toolChoice the tool choice strategy (AUTO, REQUIRED, or NONE)
     * @return {@code this}
     */
    public T toolChoice(ToolChoice toolChoice) {
        this.toolChoice = toolChoice;
        return self();
    }

    /**
     * Forces the model to use a specific tool by name.
     *
     * @param toolChoiceName the name of the tool to use
     * @return {@code this}
     */
    public T toolChoiceName(String toolChoiceName) {
        this.toolChoiceName = toolChoiceName;
        return self();
    }

    /**
     * Disables parallel tool use, forcing the model to use only one tool at a time.
     *
     * @param disableParallelToolUse true to disable parallel tool use
     * @return {@code this}
     */
    public T disableParallelToolUse(Boolean disableParallelToolUse) {
        this.disableParallelToolUse = disableParallelToolUse;
        return self();
    }

    /**
     * Specifies server tools to be included in each request to the Anthropic API.
     *
     * <p>Server tools are Anthropic-hosted tools like web search and code execution.</p>
     *
     * <p>Example:</p>
     * <pre>{@code
     * AnthropicServerTool webSearchTool = AnthropicServerTool.builder()
     *     .type("web_search_20250305")
     *     .name("web_search")
     *     .addAttribute("max_uses", 5)
     *     .addAttribute("allowed_domains", List.of("accuweather.com"))
     *     .build();
     * }</pre>
     *
     * @param serverTools the list of server tools
     * @return {@code this}
     */
    public T serverTools(List<AnthropicServerTool> serverTools) {
        this.serverTools = serverTools;
        return self();
    }

    /**
     * Specifies server tools to be included in each request to the Anthropic API.
     *
     * @param serverTools the server tools
     * @return {@code this}
     * @see #serverTools(List)
     */
    public T serverTools(AnthropicServerTool... serverTools) {
        return serverTools(asList(serverTools));
    }

    /**
     * Controls whether to return server tool results (e.g., web_search, code_execution)
     * inside {@link dev.langchain4j.data.message.AiMessage#attributes()} under the key "server_tool_results".
     *
     * <p>Disabled by default to avoid polluting ChatMemory with potentially large data.
     * If enabled, server tool results will be stored as a {@code List<AnthropicServerToolResult>}
     * within the AiMessage attributes.</p>
     *
     * @param returnServerToolResults true to include server tool results in the response
     * @return {@code this}
     * @see #serverTools(List)
     */
    public T returnServerToolResults(Boolean returnServerToolResults) {
        this.returnServerToolResults = returnServerToolResults;
        return self();
    }

    /**
     * Specifies metadata keys from the {@link ToolSpecification#metadata()} to be included in the request.
     *
     * @param toolMetadataKeysToSend the set of metadata keys to send
     * @return {@code this}
     */
    public T toolMetadataKeysToSend(Set<String> toolMetadataKeysToSend) {
        this.toolMetadataKeysToSend = toolMetadataKeysToSend;
        return self();
    }

    /**
     * Specifies metadata keys from the {@link ToolSpecification#metadata()} to be included in the request.
     *
     * @param toolMetadataKeysToSend the metadata keys to send
     * @return {@code this}
     */
    public T toolMetadataKeysToSend(String... toolMetadataKeysToSend) {
        return toolMetadataKeysToSend(new HashSet<>(asList(toolMetadataKeysToSend)));
    }

    /**
     * Enables caching of system messages for cost optimization.
     *
     * <p>When enabled, system messages will be cached using Anthropic's prompt caching feature.
     * Requires the "prompt-caching-2024-07-31" beta flag.</p>
     *
     * @param cacheSystemMessages true to enable system message caching
     * @return {@code this}
     */
    public T cacheSystemMessages(Boolean cacheSystemMessages) {
        this.cacheSystemMessages = cacheSystemMessages;
        return self();
    }

    /**
     * Enables caching of tool definitions for cost optimization.
     *
     * <p>When enabled, tool definitions will be cached using Anthropic's prompt caching feature.
     * Requires the "prompt-caching-2024-07-31" beta flag.</p>
     *
     * @param cacheTools true to enable tool caching
     * @return {@code this}
     */
    public T cacheTools(Boolean cacheTools) {
        this.cacheTools = cacheTools;
        return self();
    }

    /**
     * Enables <a href="https://docs.anthropic.com/en/docs/build-with-claude/extended-thinking">extended thinking</a>.
     *
     * <p>Extended thinking allows Claude to reason through complex problems step by step.</p>
     *
     * @param thinkingType the thinking type (e.g., "enabled")
     * @return {@code this}
     * @see #thinkingBudgetTokens(Integer)
     */
    public T thinkingType(String thinkingType) {
        this.thinkingType = thinkingType;
        return self();
    }

    /**
     * Configures the token budget for <a href="https://docs.anthropic.com/en/docs/build-with-claude/extended-thinking">extended thinking</a>.
     *
     * @param thinkingBudgetTokens the maximum tokens for thinking
     * @return {@code this}
     * @see #thinkingType(String)
     */
    public T thinkingBudgetTokens(Integer thinkingBudgetTokens) {
        this.thinkingBudgetTokens = thinkingBudgetTokens;
        return self();
    }

    /**
     * Controls whether to return thinking/reasoning text (if available) inside
     * {@link dev.langchain4j.data.message.AiMessage#thinking()}.
     *
     * <p>Please note that this does not enable thinking/reasoning for the LLM;
     * it only controls whether to parse the {@code thinking} field from the API response
     * and return it inside the AiMessage.</p>
     *
     * <p>Disabled by default. If enabled, the thinking text will be stored within the AiMessage
     * and may be persisted. If enabled, thinking signatures will also be stored and returned
     * inside the AiMessage attributes.</p>
     *
     * @param returnThinking true to include thinking in the response
     * @return {@code this}
     * @see #thinkingType(String)
     * @see #thinkingBudgetTokens(Integer)
     * @see #sendThinking(Boolean)
     */
    public T returnThinking(Boolean returnThinking) {
        this.returnThinking = returnThinking;
        return self();
    }

    /**
     * Controls whether to send thinking/reasoning text to the LLM in follow-up requests.
     *
     * <p>Enabled by default. If enabled, the contents of
     * {@link dev.langchain4j.data.message.AiMessage#thinking()} will be sent in the API request.
     * If enabled, thinking signatures (inside the AiMessage attributes) will also be sent.</p>
     *
     * @param sendThinking true to send thinking in follow-up requests
     * @return {@code this}
     * @see #thinkingType(String)
     * @see #thinkingBudgetTokens(Integer)
     * @see #returnThinking(Boolean)
     */
    public T sendThinking(Boolean sendThinking) {
        this.sendThinking = sendThinking;
        return self();
    }

    /**
     * Sets the timeout duration for API requests.
     *
     * @param timeout the timeout duration
     * @return {@code this}
     */
    public T timeout(Duration timeout) {
        this.timeout = timeout;
        return self();
    }

    /**
     * Enables logging of requests made to the Anthropic API.
     *
     * @param logRequests true to enable request logging
     * @return {@code this}
     */
    public T logRequests(Boolean logRequests) {
        this.logRequests = logRequests;
        return self();
    }

    /**
     * Enables logging of responses received from the Anthropic API.
     *
     * @param logResponses true to enable response logging
     * @return {@code this}
     */
    public T logResponses(Boolean logResponses) {
        this.logResponses = logResponses;
        return self();
    }

    /**
     * Sets an alternate logger to be used instead of the default one provided by LangChain4j
     * for logging requests and responses.
     *
     * @param logger the logger to use
     * @return {@code this}
     */
    public T logger(Logger logger) {
        this.logger = logger;
        return self();
    }

    /**
     * Sets the default request parameters to use for all requests.
     *
     * <p>These parameters can be overridden on a per-request basis.</p>
     *
     * @param defaultRequestParameters the default parameters
     * @return {@code this}
     */
    public T defaultRequestParameters(ChatRequestParameters defaultRequestParameters) {
        this.defaultRequestParameters = defaultRequestParameters;
        return self();
    }

    /**
     * Sets the user ID for the requests.
     *
     * <p>This should be a UUID, hash value, or other opaque identifier.
     * Anthropic may use this ID to help detect abuse.
     * Do not include any identifying information such as name, email address, or phone number.</p>
     *
     * @param userId the user identifier
     * @return {@code this}
     */
    public T userId(String userId) {
        this.userId = userId;
        return self();
    }

    /**
     * Sets custom parameters to be included in the request.
     *
     * <p>This can be used to pass additional parameters not directly supported by the builder.</p>
     *
     * @param customParameters the custom parameters map
     * @return {@code this}
     */
    public T customParameters(Map<String, Object> customParameters) {
        this.customParameters = customParameters;
        return self();
    }

    /**
     * Enables strict mode for tools, which enforces that the model only uses parameters
     * defined in the tool schema.
     *
     * <p>Requires the "structured-outputs-2025-11-13" beta flag.</p>
     *
     * @param strictTools true to enable strict tools
     * @return {@code this}
     */
    public T strictTools(Boolean strictTools) {
        this.strictTools = strictTools;
        return self();
    }
}
