package dev.langchain4j.model.mistralai;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.mistralai.MistralAiChatModelName.CODESTRAL_LATEST;
import static dev.langchain4j.model.mistralai.MistralAiChatModelName.MISTRAL_LARGE_LATEST;
import static dev.langchain4j.model.mistralai.MistralAiChatModelName.MISTRAL_MEDIUM_LATEST;
import static dev.langchain4j.model.mistralai.MistralAiChatModelName.MISTRAL_SMALL_LATEST;
import static dev.langchain4j.model.mistralai.MistralAiChatModelName.OPEN_MISTRAL_7B;
import static dev.langchain4j.model.mistralai.MistralAiChatModelName.OPEN_MIXTRAL_8X22B;
import static dev.langchain4j.model.mistralai.MistralAiChatModelName.OPEN_MIXTRAL_8x7B;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MistralAiChatModelIT {

    ToolSpecification retrievePaymentStatus = ToolSpecification.builder()
            .name("retrieve-payment-status")
            .description("Retrieve Payment Status")
            .parameters(JsonObjectSchema.builder()
                    .addStringProperty("transactionId")
                    .required("transactionId")
                    .build())
            .build();

    ChatModel ministral3b = MistralAiChatModel.builder()
            .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
            .modelName("ministral-3b-latest")
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    ChatModel defaultModel = MistralAiChatModel.builder()
            .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
            .modelName(OPEN_MISTRAL_7B)
            .temperature(0.1)
            .logRequests(true)
            .logResponses(true)
            .build();

    ChatModel openMixtral8x22BModel = MistralAiChatModel.builder()
            .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
            .modelName(OPEN_MIXTRAL_8X22B)
            .temperature(0.1)
            .logRequests(true)
            .logResponses(true)
            .build();

    ChatModel codestral = MistralAiChatModel.builder()
            .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
            .modelName(CODESTRAL_LATEST)
            .logRequests(true)
            .logResponses(true)
            .build();

    ChatModel openMistralNemo = MistralAiChatModel.builder()
            .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
            .modelName("open-mistral-nemo")
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
        ChatResponse response = defaultModel.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).contains("Lima");

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
        ChatModel model = MistralAiChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(OPEN_MISTRAL_7B)
                .maxTokens(4)
                .build();

        // given
        UserMessage userMessage = userMessage("What is the capital of Peru?");

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).isNotBlank();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }

    // https://docs.mistral.ai/platform/guardrailing/
    @Test
    void should_generate_system_prompt_to_enforce_guardrails() {
        // given
        ChatModel model = MistralAiChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(OPEN_MISTRAL_7B)
                .safePrompt(true)
                .temperature(0.0)
                .build();

        // given
        UserMessage userMessage = userMessage("Hello, my name is Carlos");

        // then
        ChatResponse response = model.chat(userMessage);

        // then
        AiMessage aiMessage = response.aiMessage();
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
        ChatResponse response = defaultModel.chat(userMessage1, userMessage2, userMessage3);

        // then
        assertThat(response.aiMessage().text()).contains("Lima");
        assertThat(response.aiMessage().text()).contains("Paris");
        assertThat(response.aiMessage().text()).contains("Ottawa");

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
        ChatModel model = MistralAiChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(OPEN_MIXTRAL_8x7B)
                .temperature(0.1)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = userMessage("Quelle est la capitale du Pérou?");

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).contains("Lima");

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
        ChatModel model = MistralAiChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(OPEN_MIXTRAL_8x7B)
                .temperature(0.1)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = userMessage("¿Cuál es la capital de Perú?");

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).contains("Lima");

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
        ChatModel model = MistralAiChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(MISTRAL_MEDIUM_LATEST)
                .maxTokens(10)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = userMessage("What is the capital of Peru?");

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).contains("Lima");

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

        ChatRequest request = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(retrievePaymentStatus)
                .build();

        // when
        ChatResponse response = openMixtral8x22BModel.chat(request);

        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest =
                aiMessage.toolExecutionRequests().get(0);
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
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("transactionId")
                        .required("transactionId")
                        .build())
                .build();

        List<ChatMessage> chatMessages = new ArrayList<>();
        UserMessage userMessage = userMessage("What is the status of transaction T123?");

        chatMessages.add(userMessage);
        List<ToolSpecification> toolSpecifications = List.of(retrievePaymentStatus, retrievePaymentDate);

        ChatRequest request = ChatRequest.builder()
                .messages(chatMessages)
                .toolSpecifications(toolSpecifications)
                .build();

        // when
        ChatResponse response = openMixtral8x22BModel.chat(request);

        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest =
                aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("retrieve-payment-status");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"transactionId\":\"T123\"}");

        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        chatMessages.add(aiMessage);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage =
                ToolExecutionResultMessage.from(toolExecutionRequest, "{\"status\": \"PAID\"}");
        chatMessages.add(toolExecutionResultMessage);

        // when
        ChatResponse response2 = openMixtral8x22BModel.chat(chatMessages);

        // then
        AiMessage aiMessage2 = response2.aiMessage();
        assertThat(aiMessage2.text()).containsIgnoringCase("T123");
        assertThat(aiMessage2.text()).containsIgnoringCase("paid");
        assertThat(aiMessage2.toolExecutionRequests()).isEmpty();

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
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("transactionId")
                        .required("transactionId")
                        .build())
                .build();

        List<ChatMessage> chatMessages = new ArrayList<>();
        UserMessage userMessage = userMessage("What is the payment date of transaction T123?");
        chatMessages.add(userMessage);

        ChatRequest request = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(retrievePaymentDate)
                .build();

        // when
        ChatResponse response = openMixtral8x22BModel.chat(request);

        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest =
                aiMessage.toolExecutionRequests().get(0);
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
        ToolExecutionResultMessage toolExecutionResultMessage =
                ToolExecutionResultMessage.from(toolExecutionRequest, "{\"date\": \"2024-03-11\"}");
        chatMessages.add(toolExecutionResultMessage);

        // when
        ChatResponse response2 = openMixtral8x22BModel.chat(chatMessages);

        // then
        AiMessage aiMessage2 = response2.aiMessage();
        assertThat(aiMessage2.text()).containsIgnoringCase("T123");
        assertThat(aiMessage2.text()).containsIgnoringWhitespaces("March 11, 2024");
        assertThat(aiMessage2.toolExecutionRequests()).isEmpty();

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

        ChatModel mistralLargeModel = MistralAiChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(MISTRAL_LARGE_LATEST)
                .temperature(0.1)
                .responseFormat(ResponseFormat.JSON)
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        String json = mistralLargeModel.chat(userMessage);

        // then
        assertThat(json).isEqualToIgnoringWhitespace(expectedJson);
    }

    @Test
    void should_fallback_to_default_format_when_no_message_response_format_given() {
        // given
        String userMessage = "Return JSON with two fields: transactionId and status with the values T123 and paid.";

        String expectedJson = "{\"transactionId\":\"T123\",\"status\":\"paid\"}";

        ChatModel mistralSmallModel = MistralAiChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(MISTRAL_SMALL_LATEST)
                .temperature(0.1)
                .logRequests(true)
                .logResponses(true)
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormatType.JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("TransactionStatus")
                                .rootElement(JsonObjectSchema.builder()
                                        .addStringProperty("transactionId")
                                        .addStringProperty("status")
                                        .build())
                                .build())
                        .build())
                .build();

        // when
        String json = mistralSmallModel.chat(userMessage);

        // then
        assertThat(json).isEqualToIgnoringWhitespace(expectedJson);
    }

    @Test
    void should_execute_multiple_tools_then_answer() {
        // given
        ToolSpecification retrievePaymentDate = ToolSpecification.builder()
                .name("retrieve-payment-date")
                .description("Retrieve Payment Date")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("transactionId")
                        .required("transactionId")
                        .build())
                .build();

        List<ChatMessage> chatMessages = new ArrayList<>();
        UserMessage userMessage = userMessage("What is the status and the payment date of transaction T123?");

        chatMessages.add(userMessage);
        List<ToolSpecification> toolSpecifications = List.of(retrievePaymentStatus, retrievePaymentDate);

        ChatRequest request = ChatRequest.builder()
                .messages(chatMessages)
                .toolSpecifications(toolSpecifications)
                .build();

        // when
        ChatResponse response = ministral3b.chat(request);

        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(2);

        ToolExecutionRequest toolExecutionRequest1 =
                aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest1.name()).isEqualTo("retrieve-payment-status");
        assertThat(toolExecutionRequest1.arguments()).isEqualToIgnoringWhitespace("{\"transactionId\":\"T123\"}");

        ToolExecutionRequest toolExecutionRequest2 =
                aiMessage.toolExecutionRequests().get(1);
        assertThat(toolExecutionRequest2.name()).isEqualTo("retrieve-payment-date");
        assertThat(toolExecutionRequest2.arguments()).isEqualToIgnoringWhitespace("{\"transactionId\":\"T123\"}");

        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        chatMessages.add(aiMessage);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage1 =
                ToolExecutionResultMessage.from(toolExecutionRequest1, "{\"status\": \"PAID\"}");
        chatMessages.add(toolExecutionResultMessage1);
        ToolExecutionResultMessage toolExecutionResultMessage2 =
                ToolExecutionResultMessage.from(toolExecutionRequest2, "{\"date\": \"2024-03-11\"}");
        chatMessages.add(toolExecutionResultMessage2);

        // when
        ChatResponse response2 = ministral3b.chat(chatMessages);

        // then
        AiMessage aiMessage2 = response2.aiMessage();
        assertThat(aiMessage2.text()).contains("T123");
        assertThat(aiMessage2.text()).containsIgnoringCase("paid");

        assertThat(List.of("March 11, 2024", "2024-03-11"))
                .anySatisfy(date -> assertThat(aiMessage2.text()).containsIgnoringWhitespaces(date));
        assertThat(aiMessage2.toolExecutionRequests()).isEmpty();

        TokenUsage tokenUsage2 = response2.tokenUsage();
        assertThat(tokenUsage2.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage2.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage2.totalTokenCount())
                .isEqualTo(tokenUsage2.inputTokenCount() + tokenUsage2.outputTokenCount());

        assertThat(response2.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_code_generation_using_model_openCodestralMamba_and_return_finishReason() {

        // given
        UserMessage userMessage = userMessage("Write a java code for fibonacci");

        // when
        ChatResponse response = codestral.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).isNotBlank();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_execute_multiple_tools_using_model_openMistralNemo_then_answer() {

        // given
        ToolSpecification retrievePaymentDate = ToolSpecification.builder()
                .name("retrieve-payment-date")
                .description("Retrieve Payment Date")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("transactionId")
                        .required("transactionId")
                        .build())
                .build();

        List<ChatMessage> chatMessages = new ArrayList<>();
        UserMessage userMessage = userMessage("What is the status and the payment date of transaction T123?");

        chatMessages.add(userMessage);

        ChatRequest request = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(retrievePaymentStatus, retrievePaymentDate)
                .build();

        // when
        ChatResponse response = openMistralNemo.chat(request);

        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(2);

        ToolExecutionRequest toolExecutionRequest1 =
                aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest1.name()).isEqualTo("retrieve-payment-status");
        assertThat(toolExecutionRequest1.arguments()).isEqualToIgnoringWhitespace("{\"transactionId\":\"T123\"}");

        ToolExecutionRequest toolExecutionRequest2 =
                aiMessage.toolExecutionRequests().get(1);
        assertThat(toolExecutionRequest2.name()).isEqualTo("retrieve-payment-date");
        assertThat(toolExecutionRequest2.arguments()).isEqualToIgnoringWhitespace("{\"transactionId\":\"T123\"}");

        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        chatMessages.add(aiMessage);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage1 =
                ToolExecutionResultMessage.from(toolExecutionRequest1, "{\"status\": \"PAID\"}");
        chatMessages.add(toolExecutionResultMessage1);
        ToolExecutionResultMessage toolExecutionResultMessage2 =
                ToolExecutionResultMessage.from(toolExecutionRequest2, "{\"date\": \"2024-03-11\"}");
        chatMessages.add(toolExecutionResultMessage2);

        // when
        ChatResponse response2 = openMistralNemo.chat(chatMessages);

        // then
        AiMessage aiMessage2 = response2.aiMessage();
        assertThat(aiMessage2.text()).contains("T123");
        assertThat(aiMessage2.text()).containsIgnoringCase("paid");
        assertThat(List.of("March 11, 2024", "2024-03-11"))
                .anySatisfy(date -> assertThat(aiMessage2.text()).containsIgnoringWhitespaces(date));

        assertThat(aiMessage2.text()).contains("11", "2024");
        assertThat(aiMessage2.toolExecutionRequests()).isEmpty();

        TokenUsage tokenUsage2 = response2.tokenUsage();
        assertThat(tokenUsage2.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage2.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage2.totalTokenCount())
                .isEqualTo(tokenUsage2.inputTokenCount() + tokenUsage2.outputTokenCount());

        assertThat(response2.finishReason()).isEqualTo(STOP);
    }
}
