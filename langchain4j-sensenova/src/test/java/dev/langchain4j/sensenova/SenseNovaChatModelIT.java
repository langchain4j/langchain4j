package dev.langchain4j.sensenova;

import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.sensenova.SenseNovaChatModel;
import dev.langchain4j.model.sensenova.chat.KnowledgeInjectionMode;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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

@EnabledIfEnvironmentVariable(named = "SENSENOVA_API_KEY_ID", matches = ".+")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SenseNovaChatModelIT {
    private String apiKeyId = System.getenv("SENSENOVA_API_KEY_ID");
    private String apiKeySecret = System.getenv("SENSENOVA_API_KEY_SECRET");

    SenseNovaChatModel chatModel = SenseNovaChatModel.builder()
            .apiKeyId(apiKeyId)
            .apiKeySecret(apiKeySecret)
            .logRequests(true)
            .logResponses(true)
            .maxRetries(1)
            .build();

    ToolSpecification calculator = ToolSpecification.builder()
            .name("calculator")
            .description("returns a sum of two numbers")
            .addParameter("first", INTEGER, JsonSchemaProperty.from("description", "first number"))
            .addParameter("second", INTEGER, JsonSchemaProperty.from("description", "second number"))
            .build();

    @Test
    @Order(1)
    void should_generate_answer_and_return_token_usage_and_finish_reason_stop() {

        sleep();

        // given
        UserMessage userMessage = userMessage("中国首都在哪里");

        // when
        Response<AiMessage> response = chatModel.generate(userMessage);

        // then
        assertThat(response.content().text()).contains("北京");

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    @Order(2)
    void should_generate_answer_via_web_search_plugin() {

        sleep();

        // given
        UserMessage userMessage = userMessage("中国首都在哪里");

        ToolSpecification webSearch = ToolSpecification.builder()
                .name("plugin_web_search")
                .description("returns content from internet")
                .addParameter("searchEnable", JsonSchemaProperty.BOOLEAN, JsonSchemaProperty.from("value", true))
                .addParameter("resultEnable", JsonSchemaProperty.BOOLEAN, JsonSchemaProperty.from("value", true))
                .build();

        // when
        Response<AiMessage> response = chatModel.generate(singletonList(userMessage), singletonList(webSearch));

        // then
        assertThat(response.content().text()).contains("北京");

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    @Order(3)
    void should_generate_answer_via_associated_knowledge_plugin() {

        sleep();

        // given
        UserMessage userMessage = userMessage("今天下午几点吃饭");

        ToolSpecification associatedKnowledge = ToolSpecification.builder()
                .name("plugin_associated_knowledge")
                .description("returns content from associated knowledge")
                .addParameter("content", JsonSchemaProperty.STRING, JsonSchemaProperty.from("value", "大家决定今天下午四点钟去吃饭"))
                .addParameter("mode", JsonSchemaProperty.STRING, JsonSchemaProperty.from("value", KnowledgeInjectionMode.OVERRIDE))
                .build();

        // when
        Response<AiMessage> response = chatModel.generate(singletonList(userMessage), singletonList(associatedKnowledge));

        // then
        assertThat(response.content().text()).contains("四点");

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    @Order(4)
    void should_execute_a_tool_then_answer() {

        sleep();

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

        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "4");
        List<ChatMessage> messages = asList(userMessage, aiMessage, toolExecutionResultMessage);

        // when
        Response<AiMessage> secondResponse = chatModel.generate(messages);

        // then
        AiMessage secondAiMessage = secondResponse.content();
        assertThat(secondAiMessage.text()).contains("4");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();

        TokenUsage secondTokenUsage = secondResponse.tokenUsage();
        assertThat(secondTokenUsage.totalTokenCount())
                .isEqualTo(secondTokenUsage.inputTokenCount() + secondTokenUsage.outputTokenCount());

        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    private void sleep() {
        try {
            Thread.sleep(1000L);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}