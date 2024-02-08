package dev.langchain4j.model.azure;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIServiceVersion;
import com.azure.ai.openai.models.*;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.FinishReason;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InternalAzureOpenAiHelperTest {

    @Test
    void setupOpenAIClientShouldReturnClientWithCorrectConfiguration() {
        String endpoint = "test-endpoint";
        String serviceVersion = "test-service-version";
        String apiKey = "test-api-key";
        Duration timeout = Duration.ofSeconds(30);
        Integer maxRetries = 5;
        boolean logRequestsAndResponses = true;

        OpenAIClient client = InternalAzureOpenAiHelper.setupOpenAIClient(endpoint, serviceVersion, apiKey, timeout, maxRetries, null, logRequestsAndResponses);

        assertNotNull(client);
    }

    @Test
    void getOpenAIServiceVersionShouldReturnCorrectVersion() {
        String serviceVersion = "2023-05-15";

        OpenAIServiceVersion version = InternalAzureOpenAiHelper.getOpenAIServiceVersion(serviceVersion);

        assertEquals(serviceVersion, version.getVersion());
    }

    @Test
    void toOpenAiMessagesShouldReturnCorrectMessages() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new UserMessage("test-user", "test-message"));

        List<ChatRequestMessage> openAiMessages = InternalAzureOpenAiHelper.toOpenAiMessages(messages);

        assertEquals(messages.size(), openAiMessages.size());
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
        assertEquals(FinishReason.STOP, finishReason);
    }
}
