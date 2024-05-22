package dev.langchain4j.model.cohere;

import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.STRING;
import static dev.langchain4j.data.message.ToolExecutionResultMessage.from;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class CohereChatModelIT {

    ChatLanguageModel model = CohereChatModel.builder()
            .apiKey(System.getenv("COHERE_API_KEY"))
            .maxTokens(3000)
            .temperature(0.35)
            .logRequests(true)
            .logResponses(true)
            .build();

    ToolSpecification queryDailySalesReport = ToolSpecification.builder()
            .name("query_daily_sales_report")
            .description("Connects to a database to retrieve overall sales volumes " +
                    "and sales information for a given day.")
            .addParameter(
                    "day",
                    STRING,
                    new JsonSchemaProperty("description", "Retrieves sales data for this day, formatted as YYYY-MM-DD.")
            )
            .build();

    ToolSpecification queryProductCatalog = ToolSpecification.builder()
            .name("query_product_catalog")
            .description("Connects to a product catalog with information about all the products being sold," +
                    " including categories, prices, and stock levels.")
            .addParameter(
                    "category",
                    STRING,
                    new JsonSchemaProperty("description",
                            "Retrieves product information data for all products in this category."))
            .build();

    @Test
    void should_generate_answer_and_return_token_usage_and_finish_reason_stop() {

        // given
        UserMessage userMessage = userMessage("What is the capital of Germany?");

        // when
        Response<AiMessage> response = model.generate(userMessage);
        System.out.println(response);

        // then
        assertThat(response.content().text()).contains("Berlin");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(1);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(1);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

    }

    @Test
    void should_respect_system_message() {

        // given
        SystemMessage systemMessage = SystemMessage.from("You are a professional translator into German language");
        UserMessage userMessage = UserMessage.from("Translate: I love you");

        // when
        Response<AiMessage> response = model.generate(systemMessage, userMessage);
        System.out.println(response);

        // then
        assertThat(response.content().text()).containsIgnoringCase("liebe");
    }

    @Test
    void should_execute_a_tool_then_answer() {

        // given
        List<ToolSpecification> toolSpecifications = new ArrayList<>();
        toolSpecifications.add(queryDailySalesReport);
        toolSpecifications.add(queryProductCatalog);
        String preamble = "## Task & Context\n" +
                "You help people answer their questions and other requests interactively. " +
                "You will be asked a very wide array of requests on all kinds of topics. " +
                "You will be equipped with a wide range of search engines or similar tools to help you, " +
                "which you use to research your answer. " +
                "You should focus on serving the user's needs as best you can, which will be wide-ranging.\n" +
                "\n" +
                "## Style Guide\n" +
                "Unless the user asks for a different style of answer, you should answer in full sentences, " +
                "using proper grammar and spelling.";
        SystemMessage systemMessage = SystemMessage.systemMessage(preamble);
        UserMessage userMessage = userMessage("Can you provide a sales summary for 29th September 2023," +
                " and also give me some details about the products in the 'Electronics' category," +
                " for example their prices and stock levels?");
        List<ChatMessage> input = new ArrayList<>();
        input.add(systemMessage);
        input.add(userMessage);

        // when
        Response<AiMessage> response = model.generate(input, toolSpecifications);

        // then
        AiMessage aiMessage = response.content();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(2);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        if ("query_daily_sales_report".equals(toolExecutionRequest.name())) {
            assertThat(toolExecutionRequest.arguments()).contains("\"day\": \"2023-09-29\"");
        } else {
            assertThat(toolExecutionRequest.arguments()).contains("\"category\": \"Electronics\"");
        }

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        // given
        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest,
                "29th September 2023 has No Data Available");
        List<ChatMessage> messages = asList(userMessage, aiMessage, toolExecutionResultMessage);

        // when
        Response<AiMessage> secondResponse = model.generate(messages, toolSpecifications);

        // then
        AiMessage secondAiMessage = secondResponse.content();
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();

        TokenUsage secondTokenUsage = secondResponse.tokenUsage();
        assertThat(secondTokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(secondTokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(secondTokenUsage.totalTokenCount())
                .isEqualTo(secondTokenUsage.inputTokenCount() + secondTokenUsage.outputTokenCount());

    }
}
