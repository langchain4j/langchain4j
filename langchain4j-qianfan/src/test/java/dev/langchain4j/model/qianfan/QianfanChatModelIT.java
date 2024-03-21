package dev.langchain4j.model.qianfan;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.INTEGER;
import static dev.langchain4j.data.message.ToolExecutionResultMessage.from;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "QIANFAN_API_KEY", matches = ".+")
class QianfanChatModelIT {

    //see your api key and secret key here: https://console.bce.baidu.com/qianfan/ais/console/applicationConsole/application
    private String apiKey = System.getenv("QIANFAN_API_KEY");
    private String secretKey = System.getenv("QIANFAN_SECRET_KEY");

    QianfanChatModel model = QianfanChatModel.builder().modelName("ERNIE-Bot 4.0").temperature(0.7).topP(1.0).maxRetries(1)
            .apiKey(apiKey)
            .secretKey(secretKey)
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
        Response<AiMessage> response = model.generate(userMessage);

        // then
        assertThat(response.content().text()).contains("北京");

        assertThat(response.finishReason()).isEqualTo(STOP);
    }


    @Test
    void should_execute_a_tool_then_answer() {

        // given
        UserMessage userMessage = userMessage("2+2=?");
        List<ToolSpecification> toolSpecifications = singletonList(calculator);

        // when
        Response<AiMessage> response = model.generate(singletonList(userMessage), toolSpecifications);

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

        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "4");
        List<ChatMessage> messages = asList(userMessage, aiMessage, toolExecutionResultMessage);

        // when
        Response<AiMessage> secondResponse = model.generate(messages);

        // then
        AiMessage secondAiMessage = secondResponse.content();
        assertThat(secondAiMessage.text()).contains("4");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();

        TokenUsage secondTokenUsage = secondResponse.tokenUsage();
        assertThat(secondTokenUsage.totalTokenCount())
                .isEqualTo(secondTokenUsage.inputTokenCount() + secondTokenUsage.outputTokenCount());

        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }


    @Test
    void should_generate_valid_json() {
        QianfanChatModel model = QianfanChatModel.builder().modelName("ERNIE-Bot 4.0").temperature(0.7).topP(1.0).maxRetries(1)
                .apiKey(apiKey)
                .secretKey(secretKey)
                .responseFormat("json_object")
                .build();

        //given
        String userMessage = "Return JSON with two fields: name and surname of Klaus Heisler. ";
        String expectedJson = "{\"name\": \"Klaus\", \"surname\": \"Heisler\"}";

        assertThat(model.generate(userMessage)).isEqualToIgnoringWhitespace(expectedJson);


    }

    @Test
    void should_generate_answer_with_system_message() {

        // given
        UserMessage userMessage = userMessage("Where is the capital of China");

        SystemMessage systemMessage = SystemMessage.from("Please add the word hello before each answer");

        // when
        Response<AiMessage> response = model.generate(userMessage, systemMessage);

        // then
        assertThat(response.content().text()).containsIgnoringCase("hello");

    }

    @Test
    void should_generate_answer_with_even_number_of_messages() {

        // Assume this history message has been removed because of the chat memory's sliding window mechanism.
        UserMessage historyMessage = userMessage("Where is the capital of China");

        AiMessage aiMessage = AiMessage.aiMessage("Hello, The capital of China is Beijing.");

        UserMessage userMessage = userMessage("What are the districts of Beijing?");

        SystemMessage systemMessage = SystemMessage.from("Please add the word hello before each answer");

        // length of message is even excluding system message.
        Response<AiMessage> response = model.generate(aiMessage, userMessage, systemMessage);

        assertThat(response.content().text()).containsIgnoringCase("hello");

    }

}