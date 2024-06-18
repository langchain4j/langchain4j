package dev.langchain4j.model.zhipu;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.TestStreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.INTEGER;
import static dev.langchain4j.data.message.ToolExecutionResultMessage.from;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.output.FinishReason.*;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "ZHIPU_API_KEY", matches = ".+")
public class ZhipuAiStreamingChatModelIT {
    private static final String apiKey = System.getenv("ZHIPU_API_KEY");

    private final ZhipuAiStreamingChatModel model = ZhipuAiStreamingChatModel.builder()
            .apiKey(apiKey)
            .logRequests(true)
            .logResponses(true)
            .build();

    ToolSpecification calculator = ToolSpecification.builder()
            .name("calculator")
            .description("returns a sum of two numbers")
            .addParameter("first", INTEGER)
            .addParameter("second", INTEGER)
            .build();

    @Test
    void should_stream_answer() {


        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();

        model.generate("Where is the capital of China? Please answer in English", handler);

        Response<AiMessage> response = handler.get();

        assertThat(response.content().text()).containsIgnoringCase("Beijing");
        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_sensitive_words_stream_answer() {
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();

        model.generate("fuck you", handler);

        Response<AiMessage> response = handler.get();

        assertThat(response.content().text()).isBlank();

        assertThat(response.finishReason()).isEqualTo(CONTENT_FILTER);
    }

    @Test
    void should_execute_a_tool_then_stream_answer() {

        // given
        UserMessage userMessage = userMessage("2+2=?");
        List<ToolSpecification> toolSpecifications = singletonList(calculator);

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();

        model.generate(singletonList(userMessage), toolSpecifications, handler);

        Response<AiMessage> response = handler.get();
        AiMessage aiMessage = response.content();

        // then
        assertThat(aiMessage.text()).isNull();

        List<ToolExecutionRequest> toolExecutionRequests = aiMessage.toolExecutionRequests();
        assertThat(toolExecutionRequests).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = toolExecutionRequests.get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "4");

        List<ChatMessage> messages = asList(userMessage, aiMessage, toolExecutionResultMessage);

        // when
        TestStreamingResponseHandler<AiMessage> secondHandler = new TestStreamingResponseHandler<>();

        model.generate(messages, secondHandler);

        Response<AiMessage> secondResponse = secondHandler.get();
        AiMessage secondAiMessage = secondResponse.content();

        // then
        assertThat(secondAiMessage.text()).contains("4");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();

        TokenUsage secondTokenUsage = secondResponse.tokenUsage();
        assertThat(secondTokenUsage.totalTokenCount())
                .isEqualTo(secondTokenUsage.inputTokenCount() + secondTokenUsage.outputTokenCount());

        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }


    ToolSpecification currentTime = ToolSpecification.builder()
            .name("currentTime")
            .description("currentTime")
            .build();

    @Test
    void should_execute_get_current_time_tool_and_then_answer() {
        // given
        UserMessage userMessage = userMessage("What's the time now?");
        List<ToolSpecification> toolSpecifications = singletonList(currentTime);

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(singletonList(userMessage), toolSpecifications, handler);

        // then
        Response<AiMessage> response = handler.get();
        AiMessage aiMessage = response.content();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("currentTime");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "2024-04-23 12:00:20");
        List<ChatMessage> messages = asList(userMessage, aiMessage, toolExecutionResultMessage);

        // when
        TestStreamingResponseHandler<AiMessage> secondHandler = new TestStreamingResponseHandler<>();
        model.generate(messages, secondHandler);

        // then
        Response<AiMessage> secondResponse = secondHandler.get();
        AiMessage secondAiMessage = secondResponse.content();
        assertThat(secondAiMessage.text()).contains("2024-04-23 12:00:20");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();

        TokenUsage secondTokenUsage = secondResponse.tokenUsage();
        assertThat(secondTokenUsage.totalTokenCount())
                .isEqualTo(secondTokenUsage.inputTokenCount() + secondTokenUsage.outputTokenCount());

        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }
}
