package dev.langchain4j.model.sparkdesk;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.sparkdesk.client.chat.ChatCompletionModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.INTEGER;
import static dev.langchain4j.data.message.ToolExecutionResultMessage.from;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.output.FinishReason.CONTENT_FILTER;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "SPARKDESK_API_KEY", matches = ".+")
class SparkdeskAiChatModelIT {
    private static final String API_KEY = System.getenv("SPARKDESK_API_KEY");
    private static final String API_SECRET = System.getenv("SPARKDESK_API_SECRET");
    private static final String APP_ID = System.getenv("SPARKDESK_API_ID");

    SparkdeskAiChatModel chatModel = SparkdeskAiChatModel.builder()
            .appId(APP_ID)
            .apiKey(API_KEY)
            .apiSecret(API_SECRET)
            .model(ChatCompletionModel.SPARK_MAX)
            .logRequests(true)
            .logResponses(true)
            .maxRetries(1)
            .build();

    ToolSpecification calculator = ToolSpecification.builder()
            .name("calculator")
            .description("returns a sum of two numbers")
            .addParameter("first", INTEGER)
            .addParameter("second", INTEGER)
            .build();

    @Test
    void should_generate_answer_and_return_token_usage_and_finish_reason_stop() {

        // given
        UserMessage userMessage = userMessage("中国首都在哪里");
        // when
        Response<AiMessage> response = chatModel.generate(userMessage);

        // then
        assertThat(response.content().text()).contains("北京");
    }

    @Test
    void should_sensitive_words_answer() {
        // given
        UserMessage userMessage = userMessage("fuck you");

        // when
        Response<AiMessage> response = chatModel.generate(userMessage);

        assertThat(response.content().text()).isNotBlank();

        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_execute_a_tool_then_answer() {

        // given
        UserMessage userMessage = userMessage("2+2=?");
        List<ToolSpecification> toolSpecifications = singletonList(calculator);

        // when
        Response<AiMessage> response = chatModel.generate(singletonList(userMessage), toolSpecifications);

        // then
        AiMessage aiMessage = response.content();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isNull();
        // given
        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "4");
        List<ChatMessage> messages = asList(userMessage, aiMessage, toolExecutionResultMessage);

        // when
        assertThatThrownBy(() -> chatModel.generate(messages))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SparkDesk AI model currently does not support adding tool execution result responses in the context")
                .hasNoCause();

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
        Response<AiMessage> response = chatModel.generate(singletonList(userMessage), toolSpecifications);

        // then
        AiMessage aiMessage = response.content();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("currentTime");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isNull();
        // given
        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "2024-04-23 12:00:20");
        List<ChatMessage> messages = asList(userMessage, aiMessage, toolExecutionResultMessage);

        // when
        assertThatThrownBy(() -> chatModel.generate(messages))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SparkDesk AI model currently does not support adding tool execution result responses in the context")
                .hasNoCause();

    }
}