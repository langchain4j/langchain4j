package dev.langchain4j.model.github;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.JsonSchemaElementUtils.toMap;
import static dev.langchain4j.model.output.FinishReason.CONTENT_FILTER;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;

import com.azure.ai.inference.ChatCompletionsClientBuilder;
import com.azure.ai.inference.EmbeddingsClientBuilder;
import com.azure.ai.inference.ModelServiceVersion;
import com.azure.ai.inference.models.ChatCompletionsFunctionToolCall;
import com.azure.ai.inference.models.ChatCompletionsFunctionToolDefinition;
import com.azure.ai.inference.models.ChatCompletionsOptions;
import com.azure.ai.inference.models.ChatCompletionsToolCall;
import com.azure.ai.inference.models.ChatCompletionsToolDefinition;
import com.azure.ai.inference.models.ChatMessageImageContentItem;
import com.azure.ai.inference.models.ChatMessageImageDetailLevel;
import com.azure.ai.inference.models.ChatMessageImageUrl;
import com.azure.ai.inference.models.ChatMessageTextContentItem;
import com.azure.ai.inference.models.ChatRequestAssistantMessage;
import com.azure.ai.inference.models.ChatRequestMessage;
import com.azure.ai.inference.models.ChatRequestSystemMessage;
import com.azure.ai.inference.models.ChatRequestToolMessage;
import com.azure.ai.inference.models.ChatRequestUserMessage;
import com.azure.ai.inference.models.ChatResponseMessage;
import com.azure.ai.inference.models.CompletionsFinishReason;
import com.azure.ai.inference.models.CompletionsUsage;
import com.azure.ai.inference.models.FunctionCall;
import com.azure.ai.inference.models.FunctionDefinition;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.credential.KeyCredential;
import com.azure.core.exception.HttpResponseException;
import com.azure.core.http.HttpClient;
import com.azure.core.http.ProxyOptions;
import com.azure.core.http.netty.NettyAsyncHttpClientProvider;
import com.azure.core.http.policy.ExponentialBackoffOptions;
import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.policy.HttpLogOptions;
import com.azure.core.http.policy.RetryOptions;
import com.azure.core.util.BinaryData;
import com.azure.core.util.Header;
import com.azure.core.util.HttpClientOptions;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class InternalGitHubModelHelper {

    private static final Logger logger = LoggerFactory.getLogger(InternalGitHubModelHelper.class);

    public static final String DEFAULT_GITHUB_MODELS_ENDPOINT = "https://models.inference.ai.azure.com";

    public static final String DEFAULT_USER_AGENT = "langchain4j-github-models";

    public static ChatCompletionsClientBuilder setupChatCompletionsBuilder(
            String endpoint,
            ModelServiceVersion serviceVersion,
            String gitHubToken,
            Duration timeout,
            Integer maxRetries,
            ProxyOptions proxyOptions,
            boolean logRequestsAndResponses,
            String userAgentSuffix,
            Map<String, String> customHeaders) {
        HttpClientOptions clientOptions = getClientOptions(timeout, proxyOptions, userAgentSuffix, customHeaders);
        ChatCompletionsClientBuilder chatCompletionsClientBuilder = new ChatCompletionsClientBuilder()
                .endpoint(getEndpoint(endpoint))
                .serviceVersion(getModelServiceVersion(serviceVersion))
                .httpClient(getHttpClient(clientOptions))
                .clientOptions(clientOptions)
                .httpLogOptions(getHttpLogOptions(logRequestsAndResponses))
                .retryOptions(getRetryOptions(maxRetries))
                .credential(getCredential(gitHubToken));

        return chatCompletionsClientBuilder;
    }

    public static EmbeddingsClientBuilder setupEmbeddingsBuilder(
            String endpoint,
            ModelServiceVersion serviceVersion,
            String gitHubToken,
            Duration timeout,
            Integer maxRetries,
            ProxyOptions proxyOptions,
            boolean logRequestsAndResponses,
            String userAgentSuffix,
            Map<String, String> customHeaders) {
        HttpClientOptions clientOptions = getClientOptions(timeout, proxyOptions, userAgentSuffix, customHeaders);
        EmbeddingsClientBuilder embeddingsClientBuilder = new EmbeddingsClientBuilder()
                .endpoint(getEndpoint(endpoint))
                .serviceVersion(getModelServiceVersion(serviceVersion))
                .httpClient(getHttpClient(clientOptions))
                .clientOptions(clientOptions)
                .httpLogOptions(getHttpLogOptions(logRequestsAndResponses))
                .retryOptions(getRetryOptions(maxRetries))
                .credential(getCredential(gitHubToken));

        return embeddingsClientBuilder;
    }

    private static String getEndpoint(String endpoint) {
        return isNullOrBlank(endpoint) ? DEFAULT_GITHUB_MODELS_ENDPOINT : endpoint;
    }

    public static ModelServiceVersion getModelServiceVersion(ModelServiceVersion serviceVersion) {
        return getOrDefault(serviceVersion, ModelServiceVersion.getLatest());
    }

    private static HttpClient getHttpClient(HttpClientOptions clientOptions) {
        return new NettyAsyncHttpClientProvider().createInstance(clientOptions);
    }

    private static HttpClientOptions getClientOptions(
            Duration timeout, ProxyOptions proxyOptions, String userAgentSuffix, Map<String, String> customHeaders) {
        timeout = getOrDefault(timeout, ofSeconds(60));
        HttpClientOptions clientOptions = new HttpClientOptions();
        clientOptions.setConnectTimeout(timeout);
        clientOptions.setResponseTimeout(timeout);
        clientOptions.setReadTimeout(timeout);
        clientOptions.setWriteTimeout(timeout);
        clientOptions.setProxyOptions(proxyOptions);

        String userAgent = DEFAULT_USER_AGENT;
        if (userAgentSuffix != null && !userAgentSuffix.isEmpty()) {
            userAgent = DEFAULT_USER_AGENT + "-" + userAgentSuffix;
        }
        List<Header> headers = new ArrayList<>();
        headers.add(new Header("User-Agent", userAgent));
        if (customHeaders != null) {
            customHeaders.forEach((name, value) -> headers.add(new Header(name, value)));
        }
        clientOptions.setHeaders(headers);
        return clientOptions;
    }

    private static HttpLogOptions getHttpLogOptions(boolean logRequestsAndResponses) {
        HttpLogOptions httpLogOptions = new HttpLogOptions();
        if (logRequestsAndResponses) {
            httpLogOptions.setLogLevel(HttpLogDetailLevel.BODY_AND_HEADERS);
        }
        return httpLogOptions;
    }

    private static RetryOptions getRetryOptions(Integer maxRetries) {
        maxRetries = getOrDefault(maxRetries, 2);
        ExponentialBackoffOptions exponentialBackoffOptions = new ExponentialBackoffOptions();
        exponentialBackoffOptions.setMaxRetries(maxRetries);
        return new RetryOptions(exponentialBackoffOptions);
    }

    private static KeyCredential getCredential(String gitHubToken) {
        if (gitHubToken != null) {
            return new AzureKeyCredential(gitHubToken);
        } else {
            throw new IllegalArgumentException(
                    "GitHub token is a mandatory parameter for connecting to GitHub models.");
        }
    }

    public static List<ChatRequestMessage> toAzureAiMessages(List<ChatMessage> messages) {

        return messages.stream()
                .map(InternalGitHubModelHelper::toAzureAiMessage)
                .collect(toList());
    }

    public static ChatRequestMessage toAzureAiMessage(ChatMessage message) {
        if (message instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) message;
            ChatRequestAssistantMessage chatRequestAssistantMessage =
                    new ChatRequestAssistantMessage(getOrDefault(aiMessage.text(), ""));
            chatRequestAssistantMessage.setToolCalls(toolExecutionRequestsFrom(message));
            return chatRequestAssistantMessage;
        } else if (message instanceof ToolExecutionResultMessage) {
            ToolExecutionResultMessage toolExecutionResultMessage = (ToolExecutionResultMessage) message;
            ChatRequestToolMessage chatRequestToolMessage = new ChatRequestToolMessage(toolExecutionResultMessage.id());
            chatRequestToolMessage.setContent(toolExecutionResultMessage.text());
            return chatRequestToolMessage;
        } else if (message instanceof SystemMessage) {
            SystemMessage systemMessage = (SystemMessage) message;
            return new ChatRequestSystemMessage(systemMessage.text());
        } else if (message instanceof UserMessage) {
            UserMessage userMessage = (UserMessage) message;
            ChatRequestUserMessage chatRequestUserMessage;
            if (userMessage.hasSingleText()) {
                chatRequestUserMessage = new ChatRequestUserMessage(userMessage.singleText());
            } else {
                chatRequestUserMessage = ChatRequestUserMessage.fromContentItems(userMessage.contents().stream()
                        .map(content -> {
                            if (content instanceof TextContent) {
                                String text = ((TextContent) content).text();
                                return new ChatMessageTextContentItem(text);
                            } else if (content instanceof ImageContent) {
                                ImageContent imageContent = (ImageContent) content;
                                if (imageContent.image().url() == null) {
                                    throw new IllegalArgumentException(
                                            "Image URL is not present. Base64 encoded images are not supported at the moment.");
                                }
                                ChatMessageImageUrl imageUrl = new ChatMessageImageUrl(
                                        imageContent.image().url().toString());
                                imageUrl.setDetail(ChatMessageImageDetailLevel.fromString(
                                        imageContent.detailLevel().name()));
                                return new ChatMessageImageContentItem(imageUrl);
                            } else {
                                throw new IllegalArgumentException("Unsupported content type: " + content.type());
                            }
                        })
                        .collect(toList()));
            }
            return chatRequestUserMessage;
        } else {
            throw new IllegalArgumentException("Unsupported message type: " + message.type());
        }
    }

    private static List<ChatCompletionsToolCall> toolExecutionRequestsFrom(ChatMessage message) {
        if (message instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) message;
            if (aiMessage.hasToolExecutionRequests()) {
                return aiMessage.toolExecutionRequests().stream()
                        .map(toolExecutionRequest -> new ChatCompletionsFunctionToolCall(
                                toolExecutionRequest.id(),
                                new FunctionCall(toolExecutionRequest.name(), toolExecutionRequest.arguments())))
                        .collect(toList());
            }
        }
        return null;
    }

    public static List<ChatCompletionsToolDefinition> toToolDefinitions(
            Collection<ToolSpecification> toolSpecifications) {
        return toolSpecifications.stream()
                .map(InternalGitHubModelHelper::toToolDefinition)
                .collect(toList());
    }

    private static ChatCompletionsToolDefinition toToolDefinition(ToolSpecification toolSpecification) {
        FunctionDefinition functionDefinition = new FunctionDefinition(toolSpecification.name());
        functionDefinition.setDescription(toolSpecification.description());
        functionDefinition.setParameters(getParameters(toolSpecification));
        return new ChatCompletionsFunctionToolDefinition(functionDefinition);
    }

    public static BinaryData toToolChoice(ToolSpecification toolThatMustBeExecuted) {
        FunctionCall functionCall = new FunctionCall(
                toolThatMustBeExecuted.name(),
                getParameters(toolThatMustBeExecuted).toString());
        ChatCompletionsToolCall toolToCall =
                new ChatCompletionsFunctionToolCall(toolThatMustBeExecuted.name(), functionCall);
        // TODO Revisit, does not seem right and differs from Azure OpenAI implementation.
        // TODO It should probably contain only the name of the tool that must be called (without parameters).
        return BinaryData.fromObject(toolToCall);
    }

    private static BinaryData getParameters(ToolSpecification toolSpecification) {
        return toAzureAiParameters(toolSpecification.parameters());
    }

    private static final Map<String, Object> NO_PARAMETER_DATA = new HashMap<>();

    static {
        NO_PARAMETER_DATA.put("type", "object");
        NO_PARAMETER_DATA.put("properties", new HashMap<>());
    }

    private static BinaryData toAzureAiParameters(JsonObjectSchema toolParameters) {
        Parameters parameters = new Parameters();
        if (toolParameters == null) {
            return BinaryData.fromObject(NO_PARAMETER_DATA);
        }
        parameters.setProperties(toMap(toolParameters.properties()));
        parameters.setRequired(toolParameters.required());
        return BinaryData.fromObject(parameters);
    }

    private static class Parameters {

        private final String type = "object";

        private Map<String, Map<String, Object>> properties = new HashMap<>();

        private List<String> required = new ArrayList<>();

        public String getType() {
            return this.type;
        }

        public Map<String, Map<String, Object>> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, Map<String, Object>> properties) {
            this.properties = properties;
        }

        public List<String> getRequired() {
            return required;
        }

        public void setRequired(List<String> required) {
            this.required = required;
        }
    }

    public static AiMessage aiMessageFrom(ChatResponseMessage chatResponseMessage) {
        String text = chatResponseMessage.getContent();

        if (isNullOrEmpty(chatResponseMessage.getToolCalls())) {
            return aiMessage(text);
        } else {
            List<ToolExecutionRequest> toolExecutionRequests = chatResponseMessage.getToolCalls().stream()
                    .map(chatCompletionsFunctionToolCall -> ToolExecutionRequest.builder()
                            .id(chatCompletionsFunctionToolCall.getId())
                            .name(chatCompletionsFunctionToolCall.getFunction().getName())
                            .arguments(chatCompletionsFunctionToolCall
                                    .getFunction()
                                    .getArguments())
                            .build())
                    .collect(toList());

            return isNullOrBlank(text) ? aiMessage(toolExecutionRequests) : aiMessage(text, toolExecutionRequests);
        }
    }

    public static TokenUsage tokenUsageFrom(CompletionsUsage azureAiUsage) {
        if (azureAiUsage == null) {
            return null;
        }
        return new TokenUsage(
                azureAiUsage.getPromptTokens(), azureAiUsage.getCompletionTokens(), azureAiUsage.getTotalTokens());
    }

    public static FinishReason finishReasonFrom(CompletionsFinishReason azureAiFinishReason) {
        if (azureAiFinishReason == null) {
            return null;
        } else if (azureAiFinishReason == CompletionsFinishReason.STOPPED) {
            return STOP;
        } else if (azureAiFinishReason == CompletionsFinishReason.TOKEN_LIMIT_REACHED) {
            return LENGTH;
        } else if (azureAiFinishReason == CompletionsFinishReason.CONTENT_FILTERED) {
            return CONTENT_FILTER;
        } else if (azureAiFinishReason == CompletionsFinishReason.TOOL_CALLS) {
            return TOOL_EXECUTION;
        } else {
            return null;
        }
    }

    /**
     * Support for Responsible AI (content filtered by Azure OpenAI for violence, self harm, or hate).
     */
    public static FinishReason contentFilterManagement(
            HttpResponseException httpResponseException, String contentFilterCode) {
        FinishReason exceptionFinishReason = FinishReason.OTHER;
        if (httpResponseException.getValue() instanceof Map) {
            try {
                Map<String, Object> error = (Map<String, Object>) httpResponseException.getValue();
                Object errorMap = error.get("error");
                if (errorMap instanceof Map) {
                    Map<String, Object> errorDetails = (Map<String, Object>) errorMap;
                    Object errorCode = errorDetails.get("code");
                    if (errorCode instanceof String) {
                        String code = (String) errorCode;
                        if (contentFilterCode.equals(code)) {
                            // The content was filtered by Azure OpenAI's content filter (for violence, self harm, or
                            // hate).
                            exceptionFinishReason = FinishReason.CONTENT_FILTER;
                        }
                    }
                }
            } catch (ClassCastException classCastException) {
                logger.error("Error parsing error response from Azure OpenAI", classCastException);
            }
        }
        return exceptionFinishReason;
    }

    static ChatRequest createListenerRequest(
            ChatCompletionsOptions options, List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        return ChatRequest.builder()
                .messages(messages)
                .parameters(ChatRequestParameters.builder()
                        .modelName(options.getModel())
                        .temperature(options.getTemperature())
                        .topP(options.getTopP())
                        .maxOutputTokens(options.getMaxTokens())
                        .toolSpecifications(toolSpecifications)
                        .build())
                .build();
    }

    static ChatResponse createListenerResponse(
            String responseId, String responseModel, Response<AiMessage> response) {
        if (response == null) {
            return null;
        }

        return ChatResponse.builder()
                .aiMessage(response.content())
                .metadata(ChatResponseMetadata.builder()
                        .id(responseId)
                        .modelName(responseModel)
                        .tokenUsage(response.tokenUsage())
                        .finishReason(response.finishReason())
                        .build())
                .build();
    }
}
