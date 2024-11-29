package dev.langchain4j.model.mistralai;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.mistralai.internal.api.MistralAiResponseFormatType;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.STRING;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.output.FinishReason.*;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

class MistralAiChatModelIT {

    ToolSpecification retrievePaymentStatus = ToolSpecification.builder()
            .name("retrieve-payment-status")
            .description("Retrieve Payment Status")
            .addParameter("transactionId", STRING)
            .build();

    ChatLanguageModel ministral3b = MistralAiChatModel.builder()
            .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
            .modelName("ministral-3b-latest")
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    ChatLanguageModel defaultModel = MistralAiChatModel.builder()
            .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
            .temperature(0.1)
            .logRequests(true)
            .logResponses(true)
            .build();

    ChatLanguageModel openMixtral8x22BModel = MistralAiChatModel.builder()
            .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
            .modelName(MistralAiChatModelName.OPEN_MIXTRAL_8X22B)
            .temperature(0.1)
            .logRequests(true)
            .logResponses(true)
            .build();

    @AfterEach
    void afterEach() throws InterruptedException {
        Thread.sleep(5_000); // to prevent hitting rate limits
    }

    @Test
    void should_generate_answer_and_return_token_usage_and_finish_reason_stop() {

        // given
        UserMessage userMessage = userMessage("What is the capital of Peru?");

        // when
        Response<AiMessage> response = defaultModel.generate(userMessage);

        // then
        assertThat(response.content().text()).contains("Lima");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_generate_answer_and_return_token_usage_and_finish_reason_length() {

        // given
        ChatLanguageModel model = MistralAiChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .maxTokens(4)
                .build();

        // given
        UserMessage userMessage = userMessage("What is the capital of Peru?");

        // when
        Response<AiMessage> response = model.generate(userMessage);

        // then
        assertThat(response.content().text()).isNotBlank();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }

    //https://docs.mistral.ai/platform/guardrailing/
    @Test
    void should_generate_system_prompt_to_enforce_guardrails() {
        // given
        ChatLanguageModel model = MistralAiChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .safePrompt(true)
                .temperature(0.0)
                .build();

        // given
        UserMessage userMessage = userMessage("Hello, my name is Carlos");

        // then
        Response<AiMessage> response = model.generate(userMessage);

        // then
        AiMessage aiMessage = response.content();
        assertThat(aiMessage.text()).contains("respect");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_generate_answer_and_return_token_usage_and_finish_reason_stop_with_multiple_messages() {

        // given
        UserMessage userMessage1 = userMessage("What is the capital of Peru?");
        UserMessage userMessage2 = userMessage("What is the capital of France?");
        UserMessage userMessage3 = userMessage("What is the capital of Canada?");

        // when
        Response<AiMessage> response = defaultModel.generate(userMessage1, userMessage2, userMessage3);

        // then
        assertThat(response.content().text()).contains("Lima");
        assertThat(response.content().text()).contains("Paris");
        assertThat(response.content().text()).contains("Ottawa");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_generate_answer_in_french_using_model_small_and_return_token_usage_and_finish_reason_stop() {

        // given - Mistral Small = Mistral-8X7B
        ChatLanguageModel model = MistralAiChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(MistralAiChatModelName.OPEN_MIXTRAL_8x7B)
                .temperature(0.1)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = userMessage("Quelle est la capitale du Pérou?");

        // when
        Response<AiMessage> response = model.generate(userMessage);

        // then
        assertThat(response.content().text()).contains("Lima");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_generate_answer_in_spanish_using_model_small_and_return_token_usage_and_finish_reason_stop() {

        // given - Mistral Small = Mistral-8X7B
        ChatLanguageModel model = MistralAiChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(MistralAiChatModelName.OPEN_MIXTRAL_8x7B)
                .temperature(0.1)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = userMessage("¿Cuál es la capital de Perú?");

        // when
        Response<AiMessage> response = model.generate(userMessage);

        // then
        assertThat(response.content().text()).contains("Lima");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_generate_answer_using_model_medium_and_return_token_usage_and_finish_reason_length() {

        // given - Mistral Medium 2312.
        ChatLanguageModel model = MistralAiChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(MistralAiChatModelName.MISTRAL_MEDIUM_LATEST)
                .maxTokens(10)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = userMessage("What is the capital of Peru?");

        // when
        Response<AiMessage> response = model.generate(userMessage);

        // then
        assertThat(response.content().text()).contains("Lima");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }

    @Test
    void should_execute_tool_using_model_open8x22B_and_return_finishReason_tool_execution() {

        // given
        UserMessage userMessage = userMessage("What is the status of transaction T123?");
        List<ToolSpecification> toolSpecifications = singletonList(retrievePaymentStatus);

        // when
        Response<AiMessage> response = openMixtral8x22BModel.generate(singletonList(userMessage), toolSpecifications);

        // then
        AiMessage aiMessage = response.content();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("retrieve-payment-status");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"transactionId\":\"T123\"}");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);
    }

    @Test
    void should_execute_tool_using_model_open8x22B_when_toolChoice_is_auto_and_answer() {
        // given
        ToolSpecification retrievePaymentDate = ToolSpecification.builder()
                .name("retrieve-payment-date")
                .description("Retrieve Payment Date")
                .addParameter("transactionId", STRING)
                .build();

        List<ChatMessage> chatMessages = new ArrayList<>();
        UserMessage userMessage = userMessage("What is the status of transaction T123?");

        chatMessages.add(userMessage);
        List<ToolSpecification> toolSpecifications = asList(retrievePaymentStatus, retrievePaymentDate);

        // when
        Response<AiMessage> response = openMixtral8x22BModel.generate(chatMessages, toolSpecifications);

        // then
        AiMessage aiMessage = response.content();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("retrieve-payment-status");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"transactionId\":\"T123\"}");

        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        chatMessages.add(aiMessage);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.from(toolExecutionRequest, "{\"status\": \"PAID\"}");
        chatMessages.add(toolExecutionResultMessage);

        // when
        Response<AiMessage> response2 = openMixtral8x22BModel.generate(chatMessages);

        // then
        AiMessage aiMessage2 = response2.content();
        assertThat(aiMessage2.text()).containsIgnoringCase("T123");
        assertThat(aiMessage2.text()).containsIgnoringCase("paid");
        assertThat(aiMessage2.toolExecutionRequests()).isNull();

        TokenUsage tokenUsage2 = response2.tokenUsage();
        assertThat(tokenUsage2.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage2.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage2.totalTokenCount())
                .isEqualTo(tokenUsage2.inputTokenCount() + tokenUsage2.outputTokenCount());

        assertThat(response2.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_execute_tool_forcefully_using_model_open8x22B_when_toolChoice_is_any_and_answer() {
        // given
        ToolSpecification retrievePaymentDate = ToolSpecification.builder()
                .name("retrieve-payment-date")
                .description("Retrieve Payment Date")
                .addParameter("transactionId", STRING)
                .build();

        List<ChatMessage> chatMessages = new ArrayList<>();
        UserMessage userMessage = userMessage("What is the payment date of transaction T123?");
        chatMessages.add(userMessage);

        // when
        Response<AiMessage> response = openMixtral8x22BModel.generate(singletonList(userMessage), retrievePaymentDate);

        // then
        AiMessage aiMessage = response.content();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("retrieve-payment-date");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"transactionId\":\"T123\"}");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);
        chatMessages.add(aiMessage);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.from(toolExecutionRequest, "{\"date\": \"2024-03-11\"}");
        chatMessages.add(toolExecutionResultMessage);

        // when
        Response<AiMessage> response2 = openMixtral8x22BModel.generate(chatMessages);

        // then
        AiMessage aiMessage2 = response2.content();
        assertThat(aiMessage2.text()).containsIgnoringCase("T123");
        assertThat(aiMessage2.text()).containsIgnoringWhitespaces("March 11, 2024");
        assertThat(aiMessage2.toolExecutionRequests()).isNull();

        TokenUsage tokenUsage2 = response2.tokenUsage();
        assertThat(tokenUsage2.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage2.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage2.totalTokenCount())
                .isEqualTo(tokenUsage2.inputTokenCount() + tokenUsage2.outputTokenCount());

        assertThat(response2.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_return_valid_json_object_using_model_large() {

        // given
        String userMessage = "Return JSON with two fields: transactionId and status with the values T123 and paid.";

        String expectedJson = "{\"transactionId\":\"T123\",\"status\":\"paid\"}";

        ChatLanguageModel mistralLargeModel = MistralAiChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(MistralAiChatModelName.MISTRAL_LARGE_LATEST)
                .temperature(0.1)
                .responseFormat(MistralAiResponseFormatType.JSON_OBJECT)
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        String json = mistralLargeModel.generate(userMessage);

        // then
        assertThat(json).isEqualToIgnoringWhitespace(expectedJson);
    }

    @Test
    void should_execute_multiple_tools_then_answer() {
        // given
        ToolSpecification retrievePaymentDate = ToolSpecification.builder()
                .name("retrieve-payment-date")
                .description("Retrieve Payment Date")
                .addParameter("transactionId", STRING)
                .build();

        List<ChatMessage> chatMessages = new ArrayList<>();
        UserMessage userMessage = userMessage("What is the status and the payment date of transaction T123?");

        chatMessages.add(userMessage);
        List<ToolSpecification> toolSpecifications = asList(retrievePaymentStatus, retrievePaymentDate);

        // when
        Response<AiMessage> response = ministral3b.generate(chatMessages, toolSpecifications);

        // then
        AiMessage aiMessage = response.content();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(2);

        ToolExecutionRequest toolExecutionRequest1 = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest1.name()).isEqualTo("retrieve-payment-status");
        assertThat(toolExecutionRequest1.arguments()).isEqualToIgnoringWhitespace("{\"transactionId\":\"T123\"}");

        ToolExecutionRequest toolExecutionRequest2 = aiMessage.toolExecutionRequests().get(1);
        assertThat(toolExecutionRequest2.name()).isEqualTo("retrieve-payment-date");
        assertThat(toolExecutionRequest2.arguments()).isEqualToIgnoringWhitespace("{\"transactionId\":\"T123\"}");

        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        chatMessages.add(aiMessage);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage1 = ToolExecutionResultMessage.from(toolExecutionRequest1, "{\"status\": \"PAID\"}");
        chatMessages.add(toolExecutionResultMessage1);
        ToolExecutionResultMessage toolExecutionResultMessage2 = ToolExecutionResultMessage.from(toolExecutionRequest2, "{\"date\": \"2024-03-11\"}");
        chatMessages.add(toolExecutionResultMessage2);

        // when
        Response<AiMessage> response2 = ministral3b.generate(chatMessages);

        // then
        AiMessage aiMessage2 = response2.content();
        assertThat(aiMessage2.text()).contains("T123");
        assertThat(aiMessage2.text()).containsIgnoringCase("paid");
        assertThat(aiMessage2.text()).contains("11", "2024");
        assertThat(aiMessage2.toolExecutionRequests()).isNull();

        TokenUsage tokenUsage2 = response2.tokenUsage();
        assertThat(tokenUsage2.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage2.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage2.totalTokenCount())
                .isEqualTo(tokenUsage2.inputTokenCount() + tokenUsage2.outputTokenCount());

        assertThat(response2.finishReason()).isEqualTo(STOP);
    }
}
