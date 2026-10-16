package dev.langchain4j.model.azure;

import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.aiMessageFrom;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.ai.openai.OpenAIAsyncClient;
import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIServiceVersion;
import com.azure.ai.openai.models.ChatCompletionsFunctionToolDefinition;
import com.azure.ai.openai.models.ChatCompletionsToolDefinition;
import com.azure.ai.openai.models.ChatMessageContentItem;
import com.azure.ai.openai.models.ChatMessageImageContentItem;
import com.azure.ai.openai.models.ChatMessageImageDetailLevel;
import com.azure.ai.openai.models.ChatMessageImageUrl;
import com.azure.ai.openai.models.ChatRequestMessage;
import com.azure.ai.openai.models.ChatRequestUserMessage;
import com.azure.ai.openai.models.ChatResponseMessage;
import com.azure.ai.openai.models.CompletionsFinishReason;
import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpClientProvider;
import com.azure.core.http.policy.ExponentialBackoffOptions;
import com.azure.core.http.policy.RetryOptions;
import com.azure.core.util.HttpClientOptions;
import com.azure.json.JsonOptions;
import com.azure.json.JsonReader;
import com.azure.json.implementation.DefaultJsonReader;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.ImageContent.DetailLevel;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.FinishReason;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
class InternalAzureOpenAiHelperTest {

    private static final String SERVICE_VERSION = "2024-02-01";

    @Test
    void setupOpenAIClientShouldReturnClientWithCorrectConfiguration() {
        String endpoint = "test-endpoint";
        String serviceVersion = SERVICE_VERSION;
        String apiKey = "test-api-key";
        Duration timeout = Duration.ofSeconds(30);
        Integer maxRetries = 5;
        boolean logRequestsAndResponses = true;

        OpenAIClient client = InternalAzureOpenAiHelper.setupSyncClient(
                endpoint,
                serviceVersion,
                apiKey,
                timeout,
                maxRetries,
                null,
                null,
                null,
                logRequestsAndResponses,
                null,
                null);

        assertThat(client).isNotNull();
    }

    @Test
    void setupOpenAIAsyncClientShouldReturnClientWithCorrectConfiguration() {
        String endpoint = "test-endpoint";
        String serviceVersion = SERVICE_VERSION;
        String apiKey = "test-api-key";
        Duration timeout = Duration.ofSeconds(30);
        Integer maxRetries = 5;
        boolean logRequestsAndResponses = true;

        OpenAIAsyncClient client = InternalAzureOpenAiHelper.setupAsyncClient(
                endpoint,
                serviceVersion,
                apiKey,
                timeout,
                maxRetries,
                null,
                null,
                null,
                logRequestsAndResponses,
                null,
                null);

        assertThat(client).isNotNull();
    }

    @Test
    void getOpenAIServiceVersionShouldReturnCorrectVersion() {
        String serviceVersion = "2023-05-15";

        OpenAIServiceVersion version = InternalAzureOpenAiHelper.getOpenAIServiceVersion(serviceVersion);

        assertThat(version.getVersion()).isEqualTo(serviceVersion);
    }

    @Test
    void getOpenAIServiceVersionShouldThrowIfIncorrect() {
        String serviceVersion = "1901-01-01";

        assertThatThrownBy(() -> InternalAzureOpenAiHelper.getOpenAIServiceVersion(serviceVersion))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported Azure OpenAI service version")
                .hasMessageContaining(serviceVersion);
    }

    @Test
    void getOpenAIServiceVersionShouldReturnLatestVersionIfNull() {
        OpenAIServiceVersion version = InternalAzureOpenAiHelper.getOpenAIServiceVersion(null);

        assertThat(version.getVersion())
                .isEqualTo(OpenAIServiceVersion.getLatest().getVersion());
    }

    @Test
    void getOpenAIServiceVersionShouldReturnLatestVersionIfEmpty() {
        OpenAIServiceVersion version = InternalAzureOpenAiHelper.getOpenAIServiceVersion("");

        assertThat(version.getVersion())
                .isEqualTo(OpenAIServiceVersion.getLatest().getVersion());
    }

    @Test
    void getOpenAIServiceVersionShouldReturnLatestVersionIfBlank() {
        OpenAIServiceVersion version = InternalAzureOpenAiHelper.getOpenAIServiceVersion("   ");

        assertThat(version.getVersion())
                .isEqualTo(OpenAIServiceVersion.getLatest().getVersion());
    }

    @Test
    void toOpenAiMessagesShouldReturnCorrectMessages() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new UserMessage("test-user", "test-message"));

        List<ChatRequestMessage> openAiMessages = InternalAzureOpenAiHelper.toOpenAiMessages(messages);

        assertThat(openAiMessages).hasSameSizeAs(messages);
        assertThat(openAiMessages.get(0)).isInstanceOf(ChatRequestUserMessage.class);
    }

    @Test
    void toToolDefinitionsShouldReturnCorrectToolDefinition() {
        Collection<ToolSpecification> toolSpecifications = new ArrayList<>();
        toolSpecifications.add(ToolSpecification.builder()
                .name("test-tool")
                .description("test-description")
                .build());

        List<ChatCompletionsToolDefinition> tools = InternalAzureOpenAiHelper.toToolDefinitions(toolSpecifications);

        assertThat(tools).hasSameSizeAs(toolSpecifications);
        assertThat(tools.get(0)).isInstanceOf(ChatCompletionsFunctionToolDefinition.class);
        assertThat(((ChatCompletionsFunctionToolDefinition) tools.get(0))
                        .getFunction()
                        .getName())
                .isEqualTo(toolSpecifications.iterator().next().name());
    }

    @Test
    void finishReasonFromShouldReturnCorrectFinishReason() {
        CompletionsFinishReason completionsFinishReason = CompletionsFinishReason.STOPPED;
        FinishReason finishReason = InternalAzureOpenAiHelper.finishReasonFrom(completionsFinishReason);
        assertThat(finishReason).isEqualTo(FinishReason.STOP);
    }

    @Test
    void whenToolCallsAndContentAreBothPresentShouldReturnAiMessageWithToolExecutionRequestsAndText()
            throws IOException {

        String functionName = "current_time";
        String functionArguments = "{}";
        // language=json
        String responseJson = """
                {
                        "role": "ASSISTANT",
                        "content": "Hello",
                        "tool_calls": [
                          {
                            "type": "function",
                            "function": {
                              "name": "current_time",
                              "arguments": "{}"
                            }
                          }
                        ]
                      }""";
        ChatResponseMessage responseMessage;
        try (JsonReader jsonReader = DefaultJsonReader.fromString(responseJson, new JsonOptions())) {
            responseMessage = ChatResponseMessage.fromJson(jsonReader);
        }

        AiMessage aiMessage = aiMessageFrom(responseMessage);

        assertThat(aiMessage.text()).isEqualTo("Hello");
        assertThat(aiMessage.toolExecutionRequests())
                .containsExactly(ToolExecutionRequest.builder()
                        .name(functionName)
                        .arguments(functionArguments)
                        .build());
    }

    @Test
    void resolveRetryOptions_returnsProvidedRetryOptions() {
        ExponentialBackoffOptions backoff = new ExponentialBackoffOptions();
        backoff.setMaxRetries(42);
        RetryOptions custom = new RetryOptions(backoff);
        RetryOptions result = InternalAzureOpenAiHelper.resolveRetryOptions(5, custom);
        assertThat(result).isSameAs(custom);
    }

    @Test
    void resolveRetryOptions_createsDefaultWithGivenMaxRetries() {
        RetryOptions result = InternalAzureOpenAiHelper.resolveRetryOptions(7, null);
        assertThat(result.getExponentialBackoffOptions().getMaxRetries()).isEqualTo(7);
    }

    @Test
    void resolveRetryOptions_usesDefaultMaxRetriesIfBothNull() {
        RetryOptions result = InternalAzureOpenAiHelper.resolveRetryOptions(null, null);
        assertThat(result.getExponentialBackoffOptions().getMaxRetries()).isEqualTo(2);
    }

    @Test
    void resolveRetryOptions_handlesZeroMaxRetries() {
        RetryOptions result = InternalAzureOpenAiHelper.resolveRetryOptions(0, null);
        assertThat(result.getExponentialBackoffOptions().getMaxRetries()).isZero();
    }

    @Test
    void toOpenAiMessages_shouldConvertBase64ImageToDataUri() {
        // Given
        String base64Data =
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
        String mimeType = "image/png";
        ImageContent imageContent = ImageContent.from(base64Data, mimeType);
        UserMessage userMessage = UserMessage.from("Describe this image", imageContent);
        List<ChatMessage> messages = List.of(userMessage);

        // When
        List<ChatRequestMessage> openAiMessages = InternalAzureOpenAiHelper.toOpenAiMessages(messages);

        // Then - verify conversion succeeds and message is created
        assertThat(openAiMessages).hasSize(1);
        assertThat(openAiMessages.get(0)).isInstanceOf(ChatRequestUserMessage.class);
        ChatRequestUserMessage requestMessage = (ChatRequestUserMessage) openAiMessages.get(0);

        // Verify the content is not null
        assertThat(requestMessage.getContent()).isNotNull();

        // Verify the content contains the expected data URI format in its string representation
        // The Azure SDK serializes the content to BinaryData, so we check the JSON representation
        String contentJson = requestMessage.getContent().toString();
        String expectedDataUri = "data:" + mimeType + ";base64," + base64Data;

        // The JSON should contain the image URL in data URI format
        assertThat(contentJson).contains(expectedDataUri).contains("image_url").contains("url");
    }

    @Test
    void toOpenAiMessages_shouldKeepHttpUrlAsIs() {
        // Given
        String imageUrl = "https://example.com/image.png";
        ImageContent imageContent = ImageContent.from(imageUrl);
        UserMessage userMessage = UserMessage.from("Describe this image", imageContent);
        List<ChatMessage> messages = List.of(userMessage);

        // When
        List<ChatRequestMessage> openAiMessages = InternalAzureOpenAiHelper.toOpenAiMessages(messages);

        // Then - verify conversion succeeds and message is created
        assertThat(openAiMessages).hasSize(1);
        assertThat(openAiMessages.get(0)).isInstanceOf(ChatRequestUserMessage.class);
        ChatRequestUserMessage requestMessage = (ChatRequestUserMessage) openAiMessages.get(0);

        // Verify the content is not null
        assertThat(requestMessage.getContent()).isNotNull();

        // Verify the content contains the HTTP URL as-is (not converted to data URI)
        String contentJson = requestMessage.getContent().toString();

        // The JSON should contain the image URL as provided (HTTP URL, not data URI)
        assertThat(contentJson)
                .contains(imageUrl)
                .contains("image_url")
                .contains("url")
                .doesNotContain("data:image")
                .doesNotContain("base64");
    }

    // Regression tests for https://github.com/langchain4j/langchain4j/issues/5508
    // InternalAzureOpenAiHelper.toOpenAiMessage() used to ignore ImageContent.detailLevel(),
    // so LOW/HIGH/AUTO were silently dropped and Azure applied its server default.

    private static ChatMessageImageUrl imageUrlOf(ChatRequestUserMessage requestMessage) {
        List<ChatMessageContentItem> items = requestMessage.getListContent();
        assertThat(items).isNotNull().hasSize(1);
        ChatMessageContentItem item = items.get(0);
        assertThat(item).isInstanceOf(ChatMessageImageContentItem.class);
        return ((ChatMessageImageContentItem) item).getImageUrl();
    }

    @Test
    void toOpenAiMessage_shouldForwardLowDetailLevel() {
        // Given - ImageContent.from(url, LOW) - the langchain4j default is LOW, so we
        // also implicitly cover the default-DetailLevel path.
        ImageContent imageContent = ImageContent.from("https://example.com/image.png", DetailLevel.LOW);
        UserMessage userMessage = UserMessage.from("describe", imageContent);

        // When
        ChatRequestMessage message = InternalAzureOpenAiHelper.toOpenAiMessage(userMessage);

        // Then - detail must be set on the underlying ChatMessageImageUrl (was null before #5508)
        ChatRequestUserMessage userRequest = (ChatRequestUserMessage) message;
        ChatMessageImageUrl imageUrl = imageUrlOf(userRequest);
        assertThat(imageUrl.getDetail()).isEqualTo(ChatMessageImageDetailLevel.LOW);
        assertThat(imageUrl.getDetail().toString()).isEqualTo("low");
    }

    @Test
    void toOpenAiMessage_shouldForwardHighDetailLevel() {
        // Given - explicit HIGH detail (the headline reproducer from issue #5508)
        ImageContent imageContent = ImageContent.from("https://example.com/image.png", DetailLevel.HIGH);
        UserMessage userMessage = UserMessage.from("describe", imageContent);

        // When
        ChatRequestMessage message = InternalAzureOpenAiHelper.toOpenAiMessage(userMessage);

        // Then - HIGH must reach the Azure SDK request so the high-res model is used
        ChatRequestUserMessage userRequest = (ChatRequestUserMessage) message;
        ChatMessageImageUrl imageUrl = imageUrlOf(userRequest);
        assertThat(imageUrl.getDetail()).isEqualTo(ChatMessageImageDetailLevel.HIGH);
        assertThat(imageUrl.getDetail().toString()).isEqualTo("high");
    }

    @Test
    void toOpenAiMessage_shouldForwardAutoDetailLevel() {
        // Given - explicit AUTO detail
        ImageContent imageContent = ImageContent.from("https://example.com/image.png", DetailLevel.AUTO);
        UserMessage userMessage = UserMessage.from("describe", imageContent);

        // When
        ChatRequestMessage message = InternalAzureOpenAiHelper.toOpenAiMessage(userMessage);

        // Then - AUTO must be forwarded (was previously silently dropped)
        ChatRequestUserMessage userRequest = (ChatRequestUserMessage) message;
        ChatMessageImageUrl imageUrl = imageUrlOf(userRequest);
        assertThat(imageUrl.getDetail()).isEqualTo(ChatMessageImageDetailLevel.AUTO);
        assertThat(imageUrl.getDetail().toString()).isEqualTo("auto");
    }

    @Test
    void tokenEstimatorAndRequest_shouldAgreeOnLowDetailLevel() {
        // Token-estimator / request parity for LOW. AzureOpenAiTokenCountEstimator returns 85
        // tokens for LOW images (fixed); the request must carry "detail":"low" so billed tokens
        // match the estimate.
        assertDetailParity(DetailLevel.LOW, ChatMessageImageDetailLevel.LOW);
    }

    @Test
    void tokenEstimatorAndRequest_shouldAgreeOnHighDetailLevel() {
        // Token-estimator / request parity for HIGH. Estimator returns 765 for HIGH; the request
        // must carry "detail":"high".
        assertDetailParity(DetailLevel.HIGH, ChatMessageImageDetailLevel.HIGH);
    }

    @Test
    void tokenEstimatorAndRequest_shouldAgreeOnAutoDetailLevel() {
        // Token-estimator / request parity for AUTO. Estimator returns 765 for AUTO (same bucket
        // as HIGH); the request must carry "detail":"auto".
        assertDetailParity(DetailLevel.AUTO, ChatMessageImageDetailLevel.AUTO);
    }

    private static void assertDetailParity(DetailLevel langchain4jDetail, ChatMessageImageDetailLevel azureDetail) {
        AzureOpenAiTokenCountEstimator estimator =
                new AzureOpenAiTokenCountEstimator(AzureOpenAiChatModelName.GPT_4_O);

        ImageContent image = ImageContent.from("https://example.com/x.png", langchain4jDetail);
        ChatRequestUserMessage request = (ChatRequestUserMessage)
                InternalAzureOpenAiHelper.toOpenAiMessage(UserMessage.from("hi", image));

        // The estimator must be detail-aware (was already before this fix), and the request
        // must now carry the matching detail — so estimator and request agree on token cost.
        int estimatorTokens = estimator.estimateTokenCountInMessage(UserMessage.from("hi", image));
        assertThat(estimatorTokens)
                .as("estimator must be detail-aware for " + langchain4jDetail)
                .isGreaterThan(0);
        assertThat(imageUrlOf(request).getDetail()).isEqualTo(azureDetail);
    }

    @Test
    void createHttpClient_createsDefaultClient_whenNoCustomProviderGiven() {
        // azure-core-http-netty is on the classpath as a transitive dependency, so the Azure SDK's
        // HttpClient.createDefault() discovers it and returns a usable client.
        HttpClient httpClient = InternalAzureOpenAiHelper.createHttpClient(null, new HttpClientOptions());

        assertThat(httpClient).isNotNull();
    }

    @Test
    void createHttpClient_usesCustomProvider_whenProvided() {
        HttpClient mockHttpClient = mock(HttpClient.class);
        HttpClientProvider customProvider = mock(HttpClientProvider.class);
        HttpClientOptions clientOptions = new HttpClientOptions();
        when(customProvider.createInstance(clientOptions)).thenReturn(mockHttpClient);

        HttpClient httpClient = InternalAzureOpenAiHelper.createHttpClient(customProvider, clientOptions);

        assertThat(httpClient).isSameAs(mockHttpClient);
        verify(customProvider).createInstance(clientOptions);
    }

    @Test
    void setupSyncClient_createsClient_whenNoCustomProviderGiven() {
        OpenAIClient client = InternalAzureOpenAiHelper.setupSyncClient(
                "test-endpoint",
                null,
                "test-api-key",
                null,
                null,
                null,
                null, // no custom provider → default HttpClient is discovered via the Azure SDK
                null,
                false,
                null,
                null);

        assertThat(client).isNotNull();
    }

    @Test
    void setupSyncClient_usesCustomProvider_whenProvided() {
        HttpClient mockHttpClient = mock(HttpClient.class);
        HttpClientProvider customProvider = mock(HttpClientProvider.class);
        when(customProvider.createInstance(any())).thenReturn(mockHttpClient);

        OpenAIClient client = InternalAzureOpenAiHelper.setupSyncClient(
                "test-endpoint", null, "test-api-key", null, null, null, customProvider, null, false, null, null);

        assertThat(client).isNotNull();
        verify(customProvider).createInstance(any());
    }
}
