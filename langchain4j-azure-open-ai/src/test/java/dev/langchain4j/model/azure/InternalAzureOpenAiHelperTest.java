package dev.langchain4j.model.azure;

import com.azure.ai.openai.OpenAIAsyncClient;
import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIServiceVersion;
import com.azure.ai.openai.models.*;
import com.azure.json.JsonOptions;
import com.azure.json.JsonReader;
import com.azure.json.implementation.DefaultJsonReader;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.FinishReason;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.aiMessageFrom;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class InternalAzureOpenAiHelperTest {

    @Test
    void setupOpenAIClientShouldReturnClientWithCorrectConfiguration() {
        String endpoint = "test-endpoint";
        String serviceVersion = "test-service-version";
        String apiKey = "test-api-key";
        Duration timeout = Duration.ofSeconds(30);
        Integer maxRetries = 5;
        boolean logRequestsAndResponses = true;

        OpenAIClient client = InternalAzureOpenAiHelper.setupSyncClient(endpoint, serviceVersion, apiKey, timeout, maxRetries, null, logRequestsAndResponses, null);

        assertThat(client).isNotNull();
    }

    @Test
    void setupOpenAIAsyncClientShouldReturnClientWithCorrectConfiguration() {
        String endpoint = "test-endpoint";
        String serviceVersion = "test-service-version";
        String apiKey = "test-api-key";
        Duration timeout = Duration.ofSeconds(30);
        Integer maxRetries = 5;
        boolean logRequestsAndResponses = true;

        OpenAIAsyncClient client = InternalAzureOpenAiHelper.setupAsyncClient(endpoint, serviceVersion, apiKey, timeout, maxRetries, null, logRequestsAndResponses, null);

        assertThat(client).isNotNull();
    }


    @Test
    void getOpenAIServiceVersionShouldReturnCorrectVersion() {
        String serviceVersion = "2023-05-15";

        OpenAIServiceVersion version = InternalAzureOpenAiHelper.getOpenAIServiceVersion(serviceVersion);

        assertThat(version.getVersion()).isEqualTo(serviceVersion);
    }

    @Test
    void getOpenAIServiceVersionShouldReturnLatestVersionIfIncorrect() {
        String serviceVersion = "1901-01-01";

        OpenAIServiceVersion version = InternalAzureOpenAiHelper.getOpenAIServiceVersion(serviceVersion);

        assertThat(version.getVersion()).isEqualTo(OpenAIServiceVersion.getLatest().getVersion());
    }

    @Test
    void toOpenAiMessagesShouldReturnCorrectMessages() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new UserMessage("test-user", "test-message"));

        List<ChatRequestMessage> openAiMessages = InternalAzureOpenAiHelper.toOpenAiMessages(messages);

        assertThat(openAiMessages).hasSize(messages.size());
        assertInstanceOf(ChatRequestUserMessage.class, openAiMessages.get(0));
    }

    @Test
    void toToolDefinitionsShouldReturnCorrectToolDefinition() {
        Collection<ToolSpecification> toolSpecifications = new ArrayList<>();
        toolSpecifications.add(ToolSpecification.builder()
                .name("test-tool")
                .description("test-description")
                .parameters(ToolParameters.builder().build())
                .build());

        List<ChatCompletionsToolDefinition> tools = InternalAzureOpenAiHelper.toToolDefinitions(toolSpecifications);

        assertEquals(toolSpecifications.size(), tools.size());
        assertInstanceOf(ChatCompletionsFunctionToolDefinition.class, tools.get(0));
        assertEquals(toolSpecifications.iterator().next().name(), ((ChatCompletionsFunctionToolDefinition) tools.get(0)).getFunction().getName());
    }

    @Test
    void finishReasonFromShouldReturnCorrectFinishReason() {
        CompletionsFinishReason completionsFinishReason = CompletionsFinishReason.STOPPED;
        FinishReason finishReason = InternalAzureOpenAiHelper.finishReasonFrom(completionsFinishReason);
        assertThat(finishReason).isEqualTo(FinishReason.STOP);
    }

    @Test
    void whenToolCallsAndContentAreBothPresentShouldReturnAiMessageWithToolExecutionRequestsAndText() throws IOException {

        String functionName = "current_time";
        String functionArguments = "{}";
        String responseJson = "{\n" +
                "        \"role\": \"ASSISTANT\",\n" +
                "        \"content\": \"Hello\",\n" +
                "        \"tool_calls\": [\n" +
                "          {\n" +
                "            \"type\": \"function\",\n" +
                "            \"function\": {\n" +
                "              \"name\": \"current_time\",\n" +
                "              \"arguments\": \"{}\"\n" +
                "            }\n" +
                "          }\n" +
                "        ]\n" +
                "      }";
        ChatResponseMessage responseMessage;
        try (JsonReader jsonReader = DefaultJsonReader.fromString(responseJson, new JsonOptions())) {
            responseMessage = ChatResponseMessage.fromJson(jsonReader);
        }

        AiMessage aiMessage = aiMessageFrom(responseMessage);

        assertThat(aiMessage.text()).isEqualTo("Hello");
        assertThat(aiMessage.toolExecutionRequests()).containsExactly(ToolExecutionRequest
                .builder()
                .name(functionName)
                .arguments(functionArguments)
                .build()
        );
    }
}
