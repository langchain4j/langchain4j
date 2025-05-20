package dev.langchain4j.model.jlama;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static dev.langchain4j.data.message.ToolExecutionResultMessage.from;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class JlamaChatModelToolsIT {

    static File tmpDir;
    static ChatModel model;

    @BeforeAll
    static void setup() {
        tmpDir = new File(System.getProperty("java.io.tmpdir") + File.separator + "jlama_tests");
        tmpDir.mkdirs();

        model = JlamaChatModel.builder()
                .modelName("Qwen/Qwen2.5-1.5B-Instruct")
                .modelCachePath(tmpDir.toPath())
                .temperature(0.0f)
                .maxTokens(1024)
                .build();
    }

    ToolSpecification weatherToolSpecification = ToolSpecification.builder()
            .name("get_current_temperature")
            .description("Gets the current temperature at a location")
            .parameters(JsonObjectSchema.builder()
                    .addStringProperty("location", "The location to get the temperature for, in the format \"City, Country\".")
                    .addEnumProperty("unit", List.of("celsius", "fahrenheit"), "The unit to return the temperature in, e.g. 'celsius' or 'fahrenheit'")
                    .required()
                    .build())
            .build();


    @Test
    void should_execute_a_tool_then_answer() {
        // given
        SystemMessage systemMessage = SystemMessage.systemMessage("You are a helpful assistant with tool calling capabilities. When you receive a tool call response, use the output to format an answer to the original question.");
        UserMessage userMessage = userMessage("What is the temp in Paris right now?");

        ChatRequest request = ChatRequest.builder()
                .messages(systemMessage, userMessage)
                .toolSpecifications(weatherToolSpecification)
                .build();

        // when
        ChatResponse response = model.chat(request);

        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("get_current_temperature");
        assertThat(toolExecutionRequest.arguments())
                .isEqualToIgnoringWhitespace("{\"location\": \"Paris, France\", \"unit\": \"celsius\"}");

        // given
        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "{\"unit\": \"celsius\", \"location\": \"Paris, France\", \"temperature\": \"32\"}");
        List<ChatMessage> messages = asList(systemMessage, userMessage, aiMessage, toolExecutionResultMessage);

        // when
        ChatResponse secondResponse = model.chat(messages);

        // then
        AiMessage secondAiMessage = secondResponse.aiMessage();
        assertThat(secondAiMessage.text()).contains("32");
        assertThat(secondAiMessage.toolExecutionRequests()).isEmpty();
    }
}
