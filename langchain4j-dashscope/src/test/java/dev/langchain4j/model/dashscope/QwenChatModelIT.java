package dev.langchain4j.model.dashscope;

import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;

import static dev.langchain4j.model.dashscope.QwenTestHelper.*;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
public class QwenChatModelIT {

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#nonMultimodalChatModelNameProvider")
    public void should_send_non_multimodal_messages_and_receive_response(String modelName) {
        ChatLanguageModel model = QwenChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();

        Response<AiMessage> response = model.generate(QwenTestHelper.chatMessages());
        System.out.println(response);

        assertThat(response.content().text()).containsIgnoringCase("rain");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#functionCallChatModelNameProvider")
    public void should_call_function_with_no_argument(String modelName) {
        ChatLanguageModel model = QwenChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();

        String toolName = "getCurrentDateAndTime";
        ToolSpecification noArgToolSpec = ToolSpecification.builder()
                .name(toolName)
                .description("Get the current date and time")
                .build();

        UserMessage userMessage = UserMessage.from("What time is it?");

        Response<AiMessage> response = model.generate(Collections.singletonList(userMessage), Collections.singletonList(noArgToolSpec));

        assertThat(response.content().text()).isNull();
        assertThat(response.content().toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest = response.content().toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(toolName);
        assertThat(toolExecutionRequest.arguments()).isEqualTo("{}");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#functionCallChatModelNameProvider")
    public void should_call_function_with_argument(String modelName) {
        ChatLanguageModel model = QwenChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();

        String toolName = "getCurrentWeather";
        ToolSpecification hasArgToolSpec = ToolSpecification.builder()
                .name(toolName)
                .description("Query the weather of a specified city")
                .addParameter("cityName", JsonSchemaProperty.STRING)
                .build();

        UserMessage userMessage = UserMessage.from("Weather in Beijing?");

        Response<AiMessage> response = model.generate(Collections.singletonList(userMessage), Collections.singletonList(hasArgToolSpec));

        assertThat(response.content().text()).isNull();
        assertThat(response.content().toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest = response.content().toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(toolName);
        assertThat(toolExecutionRequest.arguments()).contains("Beijing");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#functionCallChatModelNameProvider")
    public void should_call_must_be_executed_call_function(String modelName) {
        ChatLanguageModel model = QwenChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();

        String toolName = "getCurrentWeather";
        ToolSpecification mustBeExecutedTool = ToolSpecification.builder()
                .name(toolName)
                .description("Query the weather of a specified city")
                .addParameter("cityName", JsonSchemaProperty.STRING)
                .build();

        // not related to tools
        UserMessage userMessage = UserMessage.from("How many students in the classroom?");

        Response<AiMessage> response = model.generate(Collections.singletonList(userMessage), mustBeExecutedTool);

        assertThat(response.content().text()).isNull();
        assertThat(response.content().toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest = response.content().toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(toolName);
        assertThat(toolExecutionRequest.arguments()).hasSizeGreaterThan(0);
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#multimodalChatModelNameProvider")
    public void should_send_multimodal_image_url_and_receive_response(String modelName) {
        ChatLanguageModel model = QwenChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();

        Response<AiMessage> response = model.generate(multimodalChatMessagesWithImageUrl());
        System.out.println(response);

        assertThat(response.content().text()).containsIgnoringCase("dog");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#multimodalChatModelNameProvider")
    public void should_send_multimodal_image_data_and_receive_response(String modelName) {
        ChatLanguageModel model = QwenChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();

        Response<AiMessage> response = model.generate(multimodalChatMessagesWithImageData());
        System.out.println(response);

        assertThat(response.content().text()).containsIgnoringCase("parrot");
    }
}
