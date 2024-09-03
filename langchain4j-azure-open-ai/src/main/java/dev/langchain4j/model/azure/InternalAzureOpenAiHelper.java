package dev.langchain4j.model.azure;

import com.azure.ai.openai.OpenAIAsyncClient;
import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.OpenAIServiceVersion;
import com.azure.ai.openai.models.*;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.credential.KeyCredential;
import com.azure.core.credential.TokenCredential;
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
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.listener.ChatModelResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.*;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.output.FinishReason.*;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;

class InternalAzureOpenAiHelper {

    private static final Logger logger = LoggerFactory.getLogger(InternalAzureOpenAiHelper.class);

    public static final String DEFAULT_USER_AGENT = "langchain4j-azure-openai";

    public static OpenAIClient setupSyncClient(String endpoint, String serviceVersion, Object credential, Duration timeout, Integer maxRetries, ProxyOptions proxyOptions, boolean logRequestsAndResponses, String userAgentSuffix) {
        OpenAIClientBuilder openAIClientBuilder = setupOpenAIClientBuilder(endpoint, serviceVersion, credential, timeout, maxRetries, proxyOptions, logRequestsAndResponses, userAgentSuffix);
        return openAIClientBuilder.buildClient();
    }

    public static OpenAIAsyncClient setupAsyncClient(String endpoint, String serviceVersion, Object credential, Duration timeout, Integer maxRetries, ProxyOptions proxyOptions, boolean logRequestsAndResponses, String userAgentSuffix) {
        OpenAIClientBuilder openAIClientBuilder = setupOpenAIClientBuilder(endpoint, serviceVersion, credential, timeout, maxRetries, proxyOptions, logRequestsAndResponses, userAgentSuffix);
        return openAIClientBuilder.buildAsyncClient();
    }

    private static OpenAIClientBuilder setupOpenAIClientBuilder(String endpoint, String serviceVersion, Object credential, Duration timeout, Integer maxRetries, ProxyOptions proxyOptions, boolean logRequestsAndResponses, String userAgentSuffix) {
        timeout = getOrDefault(timeout, ofSeconds(60));
        HttpClientOptions clientOptions = new HttpClientOptions();
        clientOptions.setConnectTimeout(timeout);
        clientOptions.setResponseTimeout(timeout);
        clientOptions.setReadTimeout(timeout);
        clientOptions.setWriteTimeout(timeout);
        clientOptions.setProxyOptions(proxyOptions);

        String userAgent = DEFAULT_USER_AGENT;
        if (userAgentSuffix!=null && !userAgentSuffix.isEmpty()) {
            userAgent = DEFAULT_USER_AGENT + "-" + userAgentSuffix;
        }
        Header header = new Header("User-Agent", userAgent);
        clientOptions.setHeaders(Collections.singletonList(header));
        HttpClient httpClient = new NettyAsyncHttpClientProvider().createInstance(clientOptions);

        HttpLogOptions httpLogOptions = new HttpLogOptions();
        if (logRequestsAndResponses) {
            httpLogOptions.setLogLevel(HttpLogDetailLevel.BODY_AND_HEADERS);
        }

        maxRetries = getOrDefault(maxRetries, 3);
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
        return new OpenAIClientBuilder()
                .credential(tokenCredential);
    }

    public static OpenAIServiceVersion getOpenAIServiceVersion(String serviceVersion) {
        for (OpenAIServiceVersion version : OpenAIServiceVersion.values()) {
            if (version.getVersion().equals(serviceVersion)) {
                return version;
            }
        }
        return OpenAIServiceVersion.getLatest();
    }

    public static List<ChatRequestMessage> toOpenAiMessages(List<ChatMessage> messages) {

        return messages.stream()
                .map(InternalAzureOpenAiHelper::toOpenAiMessage)
                .collect(toList());
    }

    public static ChatRequestMessage toOpenAiMessage(ChatMessage message) {
        if (message instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) message;
            ChatRequestAssistantMessage chatRequestAssistantMessage = new ChatRequestAssistantMessage(getOrDefault(aiMessage.text(), ""));
            chatRequestAssistantMessage.setToolCalls(toolExecutionRequestsFrom(message));
            return chatRequestAssistantMessage;
        } else if (message instanceof ToolExecutionResultMessage) {
            ToolExecutionResultMessage toolExecutionResultMessage = (ToolExecutionResultMessage) message;
            return new ChatRequestToolMessage(toolExecutionResultMessage.text(), toolExecutionResultMessage.id());
        } else if (message instanceof SystemMessage) {
            SystemMessage systemMessage = (SystemMessage) message;
            return new ChatRequestSystemMessage(systemMessage.text());
        } else if (message instanceof UserMessage) {
            UserMessage userMessage = (UserMessage) message;
            ChatRequestUserMessage chatRequestUserMessage;
            if (userMessage.hasSingleText()) {
                chatRequestUserMessage = new ChatRequestUserMessage(((TextContent) userMessage.contents().get(0)).text());
            } else {
                chatRequestUserMessage = new ChatRequestUserMessage(userMessage.contents().stream()
                        .map(content -> {
                            if (content instanceof TextContent) {
                                String text = ((TextContent) content).text();
                                return new ChatMessageTextContentItem(text);
                            } else if (content instanceof ImageContent) {
                                ImageContent imageContent = (ImageContent) content;
                                if (imageContent.image().url() == null) {
                                    throw new IllegalArgumentException("Image URL is not present. Base64 encoded images are not supported at the moment.");
                                }
                                ChatMessageImageUrl imageUrl = new ChatMessageImageUrl(imageContent.image().url().toString());
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
        if (message instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) message;
            if (aiMessage.hasToolExecutionRequests()) {
                return aiMessage.toolExecutionRequests().stream()
                        .map(toolExecutionRequest -> new ChatCompletionsFunctionToolCall(toolExecutionRequest.id(), new FunctionCall(toolExecutionRequest.name(), toolExecutionRequest.arguments())))
                        .collect(toList());

            }
        }
        return null;
    }

    public static List<ChatCompletionsToolDefinition> toToolDefinitions(Collection<ToolSpecification> toolSpecifications) {
        return toolSpecifications.stream()
                .map(InternalAzureOpenAiHelper::toToolDefinition)
                .collect(toList());
    }

    private static ChatCompletionsToolDefinition toToolDefinition(ToolSpecification toolSpecification) {
        FunctionDefinition functionDefinition = new FunctionDefinition(toolSpecification.name());
        functionDefinition.setDescription(toolSpecification.description());
        functionDefinition.setParameters(toOpenAiParameters(toolSpecification.parameters()));
        return new ChatCompletionsFunctionToolDefinition(functionDefinition);
    }

    public static ChatCompletionsToolSelection toToolChoice(ToolSpecification toolThatMustBeExecuted) {
        FunctionCall functionCall = new FunctionCall(toolThatMustBeExecuted.name(), toOpenAiParameters(toolThatMustBeExecuted.parameters()).toString());
        ChatCompletionsToolCall toolToCall = new ChatCompletionsFunctionToolCall(toolThatMustBeExecuted.name(), functionCall);
        return ChatCompletionsToolSelection.fromBinaryData(BinaryData.fromObject(toolToCall));
    }

    private static final Map<String, Object> NO_PARAMETER_DATA = new HashMap<>();

    static {
        NO_PARAMETER_DATA.put("type", "object");
        NO_PARAMETER_DATA.put("properties", new HashMap<>());
    }

    private static BinaryData toOpenAiParameters(ToolParameters toolParameters) {
        Parameters parameters = new Parameters();
        if (toolParameters == null) {
            return BinaryData.fromObject(NO_PARAMETER_DATA);
        }
        parameters.setProperties(toolParameters.properties());
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
            List<ToolExecutionRequest> toolExecutionRequests = chatResponseMessage.getToolCalls()
                    .stream()
                    .filter(toolCall -> toolCall instanceof ChatCompletionsFunctionToolCall)
                    .map(toolCall -> (ChatCompletionsFunctionToolCall) toolCall)
                    .map(chatCompletionsFunctionToolCall ->
                            ToolExecutionRequest.builder()
                                    .id(chatCompletionsFunctionToolCall.getId())
                                    .name(chatCompletionsFunctionToolCall.getFunction().getName())
                                    .arguments(chatCompletionsFunctionToolCall.getFunction().getArguments())
                                    .build())
                    .collect(toList());

            return isNullOrBlank(text) ?
                    aiMessage(toolExecutionRequests) :
                    aiMessage(text, toolExecutionRequests);
        }
    }

    public static Image imageFrom(ImageGenerationData imageGenerationData) {
        Image.Builder imageBuilder = Image.builder()
                .revisedPrompt(imageGenerationData.getRevisedPrompt());

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

    public static TokenUsage tokenUsageFrom(CompletionsUsage openAiUsage) {
        if (openAiUsage == null) {
            return null;
        }
        return new TokenUsage(
                openAiUsage.getPromptTokens(),
                openAiUsage.getCompletionTokens(),
                openAiUsage.getTotalTokens()
        );
    }

    public static FinishReason finishReasonFrom(CompletionsFinishReason openAiFinishReason) {
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
        } else {
            return null;
        }
    }

    /**
     * Support for Responsible AI (content filtered by Azure OpenAI for violence, self harm, or hate).
     */
    public static FinishReason contentFilterManagement(HttpResponseException httpResponseException, String contentFilterCode) {
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
                            // The content was filtered by Azure OpenAI's content filter (for violence, self harm, or hate).
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

    static ChatModelRequest createModelListenerRequest(ChatCompletionsOptions options,
                                                       List<ChatMessage> messages,
                                                       List<ToolSpecification> toolSpecifications) {
        return ChatModelRequest.builder()
            .model(options.getModel())
            .temperature(options.getTemperature())
            .topP(options.getTopP())
            .maxTokens(options.getMaxTokens())
            .messages(messages)
            .toolSpecifications(toolSpecifications)
            .build();
    }

    static ChatModelResponse createModelListenerResponse(String responseId,
                                                         String responseModel,
                                                         Response<AiMessage> response) {
        if (response == null) {
            return null;
        }

        return ChatModelResponse.builder()
            .id(responseId)
            .model(responseModel)
            .tokenUsage(response.tokenUsage())
            .finishReason(response.finishReason())
            .aiMessage(response.content())
            .build();
    }
}
