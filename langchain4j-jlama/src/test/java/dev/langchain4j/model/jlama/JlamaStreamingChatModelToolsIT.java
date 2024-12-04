package dev.langchain4j.model.jlama;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TestStreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.STRING;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.description;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.enums;
import static dev.langchain4j.data.message.ToolExecutionResultMessage.from;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static org.assertj.core.api.Assertions.assertThat;

public class JlamaStreamingChatModelToolsIT {

    static File tmpDir;
    static StreamingChatLanguageModel model;


    @BeforeAll
    static void setup() {
        tmpDir = new File(System.getProperty("java.io.tmpdir") + File.separator + "jlama_tests");
        tmpDir.mkdirs();

        model = JlamaStreamingChatModel.builder()
                .modelName("Qwen/Qwen2.5-1.5B-Instruct")
                .modelCachePath(tmpDir.toPath())
                .temperature(0.0f)
                .maxTokens(1024)
                .build();
    }

    ToolSpecification weatherToolSpecification = ToolSpecification.builder()
            .name("get_current_temperature")
            .description("Gets the current temperature at a location")
            .addParameter("location", STRING, description("The location to get the temperature for, in the format \"City, Country\"."))
            .addParameter("unit", STRING, enums("celsius", "fahrenheit"), description("The unit to return the temperature in, e.g. 'celsius' or 'fahrenheit'"))
            .build();

    @Test
    void should_execute_a_tool_then_answer_streaming() {
        List<ChatMessage> chatMessages = new ArrayList<>();
        SystemMessage systemMessage = SystemMessage.systemMessage("You are a helpful assistant with tool calling capabilities. When you receive a tool call response, use the output to format an answer to the original question.");
        chatMessages.add(systemMessage);
        UserMessage userMessage = userMessage("What is the temp in Paris right now?");
        chatMessages.add(userMessage);

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(chatMessages, List.of(weatherToolSpecification), handler);
        Response<AiMessage> response = handler.get();

        // then
        AiMessage aiMessage = response.content();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        chatMessages.add(aiMessage);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("get_current_temperature");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"unit\": \"celsius\", \"location\": \"Paris, France\"}");

        // given
        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "{\"unit\": \"celsius\", \"location\": \"Paris, France\", \"temperature\": \"32\"}");
        chatMessages.add(toolExecutionResultMessage);

        // when
        handler = new TestStreamingResponseHandler<>();
        model.generate(chatMessages, handler);
        Response<AiMessage> secondResponse = handler.get();

        // then
        AiMessage secondAiMessage = secondResponse.content();
        assertThat(secondAiMessage.text()).contains("32");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();
    }
}
