package dev.langchain4j.model.jlama;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.*;
import static dev.langchain4j.data.message.ToolExecutionResultMessage.from;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class JlamaToolChatModelIT {

    static File tmpDir;
    static ChatLanguageModel model;

    @BeforeAll
    static void setup() {
        tmpDir = new File(System.getProperty("java.io.tmpdir") + File.separator + "jlama_tests");
        tmpDir.mkdirs();

        model = JlamaChatModel.builder()
                .modelName("tjake/Meta-Llama-3.1-8B-Instruct-Jlama-Q4")
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
    void should_execute_a_tool_then_answer() {
        // given
        SystemMessage systemMessage = SystemMessage.systemMessage("You are a helpful assistant with tool calling capabilities. When you receive a tool call response, use the output to format an answer to the original question.");
        UserMessage userMessage = userMessage("What is the temp in Paris right now?");
        List<ToolSpecification> toolSpecifications = singletonList(weatherToolSpecification);

        // when
        Response<AiMessage> response = model.generate(asList(systemMessage, userMessage), toolSpecifications);

        // then
        AiMessage aiMessage = response.content();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("get_current_temperature");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"unit\": \"celsius\", \"location\": \"Paris, France\"}");

        // given
        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "{\"unit\": \"celsius\", \"location\": \"Paris, France\", \"temperature\": \"32\"}");
        List<ChatMessage> messages = asList(systemMessage, userMessage, aiMessage, toolExecutionResultMessage);

        // when
        Response<AiMessage> secondResponse = model.generate(messages);

        // then
        AiMessage secondAiMessage = secondResponse.content();
        assertThat(secondAiMessage.text()).contains("32");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();
    }
}
