package dev.langchain4j.model.azure;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.JsonSchemaElementUtils.toMap;
import static dev.langchain4j.model.output.FinishReason.CONTENT_FILTER;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;

import com.azure.ai.openai.OpenAIAsyncClient;
import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.OpenAIServiceVersion;
import com.azure.ai.openai.models.ChatCompletionsFunctionToolCall;
import com.azure.ai.openai.models.ChatCompletionsFunctionToolDefinition;
import com.azure.ai.openai.models.ChatCompletionsFunctionToolDefinitionFunction;
import com.azure.ai.openai.models.ChatCompletionsJsonResponseFormat;
import com.azure.ai.openai.models.ChatCompletionsJsonSchemaResponseFormat;
import com.azure.ai.openai.models.ChatCompletionsJsonSchemaResponseFormatJsonSchema;
import com.azure.ai.openai.models.ChatCompletionsResponseFormat;
import com.azure.ai.openai.models.ChatCompletionsTextResponseFormat;
import com.azure.ai.openai.models.ChatCompletionsToolCall;
import com.azure.ai.openai.models.ChatCompletionsToolDefinition;
import com.azure.ai.openai.models.ChatCompletionsToolSelection;
import com.azure.ai.openai.models.ChatCompletionsToolSelectionPreset;
import com.azure.ai.openai.models.ChatMessageImageContentItem;
import com.azure.ai.openai.models.ChatMessageImageUrl;
import com.azure.ai.openai.models.ChatMessageTextContentItem;
import com.azure.ai.openai.models.ChatRequestAssistantMessage;
import com.azure.ai.openai.models.ChatRequestMessage;
import com.azure.ai.openai.models.ChatRequestSystemMessage;
import com.azure.ai.openai.models.ChatRequestToolMessage;
import com.azure.ai.openai.models.ChatRequestUserMessage;
import com.azure.ai.openai.models.ChatResponseMessage;
import com.azure.ai.openai.models.CompletionsFinishReason;
import com.azure.ai.openai.models.CompletionsUsage;
import com.azure.ai.openai.models.FunctionCall;
import com.azure.ai.openai.models.ImageGenerationData;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.credential.KeyCredential;
import com.azure.core.credential.TokenCredential;
import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpClientProvider;
import com.azure.core.http.ProxyOptions;
import com.azure.core.http.netty.NettyAsyncHttpClientProvider;
import com.azure.core.http.policy.ExponentialBackoffOptions;
import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.policy.HttpLogOptions;
import com.azure.core.http.policy.RetryOptions;
import com.azure.core.util.BinaryData;
import com.azure.core.util.Header;
import com.azure.core.util.HttpClientOptions;
import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Internal
class InternalAzureOpenAiHelper {

    static final String DEFAULT_USER_AGENT = "langchain4j-azure-openai";

    static OpenAIClient setupSyncClient(
            String endpoint,
            String serviceVersion,
            Object credential,
            Duration timeout,
            Integer maxRetries,
            HttpClientProvider httpClientProvider,
            ProxyOptions proxyOptions,
            boolean logRequestsAndResponses,
            String userAgentSuffix,
            Map<String, String> customHeaders) {
        OpenAIClientBuilder openAIClientBuilder = setupOpenAIClientBuilder(
                endpoint,
                serviceVersion,
                credential,
                timeout,
                maxRetries,
                httpClientProvider,
                proxyOptions,
                logRequestsAndResponses,
                userAgentSuffix,
                customHeaders);
        return openAIClientBuilder.buildClient();
    }

    static OpenAIAsyncClient setupAsyncClient(
            String endpoint,
            String serviceVersion,
            Object credential,
            Duration timeout,
            Integer maxRetries,
            HttpClientProvider httpClientProvider,
            ProxyOptions proxyOptions,
            boolean logRequestsAndResponses,
            String userAgentSuffix,
            Map<String, String> customHeaders) {
        OpenAIClientBuilder openAIClientBuilder = setupOpenAIClientBuilder(
                endpoint,
                serviceVersion,
                credential,
                timeout,
                maxRetries,
                httpClientProvider,
                proxyOptions,
                logRequestsAndResponses,
                userAgentSuffix,
                customHeaders);
        return openAIClientBuilder.buildAsyncClient();
    }

    private static OpenAIClientBuilder setupOpenAIClientBuilder(
            String endpoint,
            String serviceVersion,
            Object credential,
            Duration timeout,
            Integer maxRetries,
            HttpClientProvider httpClientProvider,
            ProxyOptions proxyOptions,
            boolean logRequestsAndResponses,
            String userAgentSuffix,
            Map<String, String> customHeaders) {
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
        httpClientProvider = getOrDefault(httpClientProvider, NettyAsyncHttpClientProvider::new);
        HttpClient httpClient = httpClientProvider.createInstance(clientOptions);

        HttpLogOptions httpLogOptions = new HttpLogOptions();
        if (logRequestsAndResponses) {
            httpLogOptions.setLogLevel(HttpLogDetailLevel.BODY_AND_HEADERS);
        }

        maxRetries = getOrDefault(maxRetries, 2);
        ExponentialBackoffOptions exponentialBackoffOptions = new ExponentialBackoffOptions();
        exponentialBackoffOptions.setMaxRetries(maxRetries);
        RetryOptions retryOptions = new RetryOptions(exponentialBackoffOptions);

        OpenAIClientBuilder openAIClientBuilder = new OpenAIClientBuilder()
                .endpoint(ensureNotBlank(endpoint, "endpoint"))
                .serviceVersion(getOpenAIServiceVersion(serviceVersion))
                .httpClient(httpClient)
                .clientOptions(clientOptions)
                .httpLogOptions(httpLogOptions)
                .retryOptions(retryOptions);

        if (credential instanceof String) {
            openAIClientBuilder.credential(new AzureKeyCredential((String) credential));
        } else if (credential instanceof KeyCredential) {
            openAIClientBuilder.credential((KeyCredential) credential);
        } else if (credential instanceof TokenCredential) {
            openAIClientBuilder.credential((TokenCredential) credential);
        } else {
            throw new IllegalArgumentException("Unsupported credential type: " + credential.getClass());
        }

        return openAIClientBuilder;
    }

    private static OpenAIClientBuilder authenticate(TokenCredential tokenCredential) {
        return new OpenAIClientBuilder().credential(tokenCredential);
    }

    static OpenAIServiceVersion getOpenAIServiceVersion(String serviceVersion) {
        for (OpenAIServiceVersion version : OpenAIServiceVersion.values()) {
            if (version.getVersion().equals(serviceVersion)) {
                return version;
            }
        }
        return OpenAIServiceVersion.getLatest();
    }

    static List<ChatRequestMessage> toOpenAiMessages(List<ChatMessage> messages) {

        return messages.stream().map(InternalAzureOpenAiHelper::toOpenAiMessage).collect(toList());
    }

    static ChatRequestMessage toOpenAiMessage(ChatMessage message) {
        if (message instanceof AiMessage aiMessage) {
            ChatRequestAssistantMessage chatRequestAssistantMessage =
                    new ChatRequestAssistantMessage(getOrDefault(aiMessage.text(), ""));
            chatRequestAssistantMessage.setToolCalls(toolExecutionRequestsFrom(message));
            return chatRequestAssistantMessage;
        } else if (message instanceof ToolExecutionResultMessage toolExecutionResultMessage) {
            return new ChatRequestToolMessage(toolExecutionResultMessage.text(), toolExecutionResultMessage.id());
        } else if (message instanceof SystemMessage systemMessage) {
            return new ChatRequestSystemMessage(systemMessage.text());
        } else if (message instanceof UserMessage userMessage) {
            ChatRequestUserMessage chatRequestUserMessage;
            if (userMessage.hasSingleText()) {
                chatRequestUserMessage = new ChatRequestUserMessage(
                        ((TextContent) userMessage.contents().get(0)).text());
            } else {
                chatRequestUserMessage = new ChatRequestUserMessage(userMessage.contents().stream()
                        .map(content -> {
                            if (content instanceof TextContent) {
                                String text = ((TextContent) content).text();
                                return new ChatMessageTextContentItem(text);
                            } else if (content instanceof ImageContent imageContent) {
                                if (imageContent.image().url() == null) {
                                    throw new UnsupportedFeatureException("Image URL is not present. "
                                            + "Base64 encoded images are not supported at the moment.");
                                }
                                ChatMessageImageUrl imageUrl = new ChatMessageImageUrl(
                                        imageContent.image().url().toString());
                                return new ChatMessageImageContentItem(imageUrl);
                            } else {
                                throw new IllegalArgumentException("Unsupported content type: " + content.type());
                            }
                        })
                        .collect(toList()));
            }
            chatRequestUserMessage.setName(nameFrom(message));
            return chatRequestUserMessage;
        } else {
            throw new IllegalArgumentException("Unsupported message type: " + message.type());
        }
    }

    private static String nameFrom(ChatMessage message) {
        if (message instanceof UserMessage) {
            return ((UserMessage) message).name();
        }

        if (message instanceof ToolExecutionResultMessage) {
            return ((ToolExecutionResultMessage) message).toolName();
        }

        return null;
    }

    private static List<ChatCompletionsToolCall> toolExecutionRequestsFrom(ChatMessage message) {
        if (message instanceof AiMessage aiMessage) {
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

    static List<ChatCompletionsToolDefinition> toToolDefinitions(
            Collection<ToolSpecification> toolSpecifications) {
        return toolSpecifications.stream()
                .map(InternalAzureOpenAiHelper::toToolDefinition)
                .collect(toList());
    }

    private static ChatCompletionsToolDefinition toToolDefinition(ToolSpecification toolSpecification) {
        ChatCompletionsFunctionToolDefinitionFunction functionDefinition =
                new ChatCompletionsFunctionToolDefinitionFunction(toolSpecification.name());
        functionDefinition.setDescription(toolSpecification.description());
        functionDefinition.setParameters(getParameters(toolSpecification));
        return new ChatCompletionsFunctionToolDefinition(functionDefinition);
    }

    static ChatCompletionsToolSelection toToolChoice(ToolChoice toolChoice) {
        ChatCompletionsToolSelectionPreset preset = switch (toolChoice) {
            case AUTO -> ChatCompletionsToolSelectionPreset.AUTO;
            case REQUIRED -> ChatCompletionsToolSelectionPreset.REQUIRED;
        };
        return new ChatCompletionsToolSelection(preset);
    }

    private static BinaryData getParameters(ToolSpecification toolSpecification) {
        return toOpenAiParameters(toolSpecification.parameters());
    }

    private static final Map<String, Object> NO_PARAMETER_DATA = new HashMap<>();

    static {
        NO_PARAMETER_DATA.put("type", "object");
        NO_PARAMETER_DATA.put("properties", new HashMap<>());
    }

    private static BinaryData toOpenAiParameters(JsonObjectSchema toolParameters) {
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

    static AiMessage aiMessageFrom(ChatResponseMessage chatResponseMessage) {
        String text = chatResponseMessage.getContent();

        if (isNullOrEmpty(chatResponseMessage.getToolCalls())) {
            return aiMessage(text);
        } else {
            List<ToolExecutionRequest> toolExecutionRequests = chatResponseMessage.getToolCalls().stream()
                    .filter(toolCall -> toolCall instanceof ChatCompletionsFunctionToolCall)
                    .map(toolCall -> (ChatCompletionsFunctionToolCall) toolCall)
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

    static Image imageFrom(ImageGenerationData imageGenerationData) {
        Image.Builder imageBuilder = Image.builder().revisedPrompt(imageGenerationData.getRevisedPrompt());

        String urlString = imageGenerationData.getUrl();
        String imageData = imageGenerationData.getBase64Data();
        if (urlString != null) {
            try {
                URI uri = new URI(urlString);
                imageBuilder.url(uri);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        } else if (imageData != null) {
            imageBuilder.base64Data(imageData);
        }

        return imageBuilder.build();
    }

    static TokenUsage tokenUsageFrom(CompletionsUsage openAiUsage) {
        if (openAiUsage == null) {
            return null;
        }
        return new TokenUsage(
                openAiUsage.getPromptTokens(), openAiUsage.getCompletionTokens(), openAiUsage.getTotalTokens());
    }

    static FinishReason finishReasonFrom(CompletionsFinishReason openAiFinishReason) {
        if (openAiFinishReason == null) {
            return null;
        } else if (openAiFinishReason == CompletionsFinishReason.STOPPED) {
            return STOP;
        } else if (openAiFinishReason == CompletionsFinishReason.TOKEN_LIMIT_REACHED) {
            return LENGTH;
        } else if (openAiFinishReason == CompletionsFinishReason.CONTENT_FILTERED) {
            return CONTENT_FILTER;
        } else if (openAiFinishReason == CompletionsFinishReason.FUNCTION_CALL) {
            return TOOL_EXECUTION;
        } else if (openAiFinishReason == CompletionsFinishReason.TOOL_CALLS) {
            return TOOL_EXECUTION;
        } else {
            return null;
        }
    }

    static ChatCompletionsResponseFormat toAzureOpenAiResponseFormat(ResponseFormat responseFormat, boolean strict) {
        if (responseFormat == null || responseFormat.type() == ResponseFormatType.TEXT) {
            return new ChatCompletionsTextResponseFormat();
        } else if (responseFormat.type() != ResponseFormatType.JSON) {
            throw new IllegalArgumentException("Unsupported response format: " + responseFormat);
        }

        JsonSchema jsonSchema = responseFormat.jsonSchema();
        if (jsonSchema == null) {
            return new ChatCompletionsJsonResponseFormat();
        } else {
            if (!(jsonSchema.rootElement() instanceof JsonObjectSchema)) {
                throw new IllegalArgumentException(
                        "For Azure OpenAI, the root element of the JSON Schema must be a JsonObjectSchema, but it was: "
                                + jsonSchema.rootElement().getClass());
            }
            ChatCompletionsJsonSchemaResponseFormatJsonSchema schema =
                    new ChatCompletionsJsonSchemaResponseFormatJsonSchema(jsonSchema.name());
            schema.setStrict(strict);
            Map<String, Object> schemaMap = toMap(jsonSchema.rootElement(), strict);
            schema.setSchema(BinaryData.fromObject(schemaMap));
            return new ChatCompletionsJsonSchemaResponseFormat(schema);
        }
    }

    static void validate(ChatRequestParameters parameters) {
        if (parameters.topK() != null) {
            throw new UnsupportedFeatureException("'topK' parameter is not supported by OpenAI");
        }
    }
}

