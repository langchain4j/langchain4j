package dev.langchain4j.model.mistralai;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.mistralai.internal.api.MistralAiResponseFormatType;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.STRING;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.mistralai.MistralAiChatModelName.MISTRAL_LARGE_LATEST;
import static dev.langchain4j.model.mistralai.MistralAiChatModelName.MISTRAL_MEDIUM_LATEST;
import static dev.langchain4j.model.mistralai.MistralAiChatModelName.MISTRAL_SMALL_LATEST;
import static dev.langchain4j.model.mistralai.MistralAiChatModelName.OPEN_MISTRAL_7B;
import static dev.langchain4j.model.mistralai.MistralAiChatModelName.OPEN_MIXTRAL_8X22B;
import static dev.langchain4j.model.mistralai.internal.api.MistralAiResponseFormatType.JSON_OBJECT;
import static dev.langchain4j.model.output.FinishReason.*;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

class MistralAiStreamingChatModelIT {

    ToolSpecification retrievePaymentStatus = ToolSpecification.builder()
            .name("retrieve-payment-status")
            .description("Retrieve Payment Status")
            .addParameter("transactionId", STRING)
            .build();

    StreamingChatLanguageModel ministral3b = MistralAiStreamingChatModel.builder()
            .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
            .modelName("ministral-3b-latest")
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    StreamingChatLanguageModel defaultModel = MistralAiStreamingChatModel.builder()
            .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
            .modelName(OPEN_MISTRAL_7B)
            .temperature(0.1)
            .logRequests(true)
            .logResponses(true)
            .build();

    StreamingChatLanguageModel openMixtral8x22BModel = MistralAiStreamingChatModel.builder()
            .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
            .modelName(OPEN_MIXTRAL_8X22B)
            .temperature(0.1)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_stream_answer_and_return_token_usage_and_finish_reason_stop() {

        // given
        UserMessage userMessage = userMessage("What is the capital of Peru?");

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        defaultModel.chat(List.of(userMessage), handler);

        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("Lima");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_stream_answer_and_return_token_usage_and_finish_reason_length() {

        // given
        StreamingChatLanguageModel model = MistralAiStreamingChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(OPEN_MISTRAL_7B)
                .maxTokens(10)
                .build();

        // given
        UserMessage userMessage = userMessage("What is the capital of Peru?");

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(List.of(userMessage), handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("Lima");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }

    //https://docs.mistral.ai/platform/guardrailing/
    @Test
    void should_stream_answer_and_system_prompt_to_enforce_guardrails() {

        // given
        StreamingChatLanguageModel model = MistralAiStreamingChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(OPEN_MISTRAL_7B)
                .safePrompt(true)
                .temperature(0.0)
                .build();

        // given
        UserMessage userMessage = userMessage("Hello, my name is Carlos");

        // then
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(List.of(userMessage), handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("respect");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_stream_answer_and_return_token_usage_and_finish_reason_stop_with_multiple_messages() {

        // given
        UserMessage userMessage1 = userMessage("What is the capital of Peru?");
        UserMessage userMessage2 = userMessage("What is the capital of France?");
        UserMessage userMessage3 = userMessage("What is the capital of Canada?");

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        defaultModel.chat(List.of(userMessage1, userMessage2, userMessage3), handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("lima");
        assertThat(response.aiMessage().text()).containsIgnoringCase("paris");
        assertThat(response.aiMessage().text()).containsIgnoringCase("ottawa");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_stream_answer_in_french_using_model_small_and_return_token_usage_and_finish_reason_stop() {

        // given - Mistral Small = Mistral-8X7B
        StreamingChatLanguageModel model = MistralAiStreamingChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(MISTRAL_SMALL_LATEST)
                .temperature(0.1)
                .build();

        UserMessage userMessage = userMessage("Quelle est la capitale du Pérou?");

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(List.of(userMessage), handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("lima");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_stream_answer_in_spanish_using_model_small_and_return_token_usage_and_finish_reason_stop() {

        // given - Mistral Small = Mistral-8X7B
        StreamingChatLanguageModel model = MistralAiStreamingChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(MISTRAL_SMALL_LATEST)
                .temperature(0.1)
                .build();

        UserMessage userMessage = userMessage("¿Cuál es la capital de Perú?");

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(List.of(userMessage), handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("lima");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_stream_answer_using_model_medium_and_return_token_usage_and_finish_reason_length() {

        // given - Mistral Medium = currently relies on an internal prototype model.
        StreamingChatLanguageModel model = MistralAiStreamingChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(MISTRAL_MEDIUM_LATEST)
                .maxTokens(10)
                .build();

        UserMessage userMessage = userMessage("What is the capital of Peru?");

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(List.of(userMessage), handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("lima");

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

        ChatRequest request = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(toolSpecifications)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        openMixtral8x22BModel.chat(request, handler);

        ChatResponse response = handler.get();

        // then
        AiMessage aiMessage = response.aiMessage();
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

        ChatRequest request = ChatRequest.builder()
                .messages(chatMessages)
                .toolSpecifications(toolSpecifications)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        openMixtral8x22BModel.chat(request, handler);
        ChatResponse response = handler.get();

        // then
        AiMessage aiMessage = response.aiMessage();
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
        TestStreamingChatResponseHandler handler2 = new TestStreamingChatResponseHandler();
        openMixtral8x22BModel.chat(chatMessages, handler2);
        ChatResponse response2 = handler2.get();

        // then
        AiMessage aiMessage2 = response2.aiMessage();
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

        ChatRequest request = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(retrievePaymentDate)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        openMixtral8x22BModel.chat(request, handler);
        ChatResponse response = handler.get();

        // then
        AiMessage aiMessage = response.aiMessage();
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
        TestStreamingChatResponseHandler handler2 = new TestStreamingChatResponseHandler();
        openMixtral8x22BModel.chat(chatMessages, handler2);
        ChatResponse response2 = handler2.get();

        // then
        AiMessage aiMessage2 = response2.aiMessage();
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

        ChatRequest request = ChatRequest.builder()
                .messages(chatMessages)
                .toolSpecifications(toolSpecifications)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        ministral3b.chat(request, handler);
        ChatResponse response = handler.get();

        // then
        AiMessage aiMessage = response.aiMessage();
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
        TestStreamingChatResponseHandler handler2 = new TestStreamingChatResponseHandler();
        ministral3b.chat(chatMessages, handler2);
        ChatResponse response2 = handler2.get();

        // then
        AiMessage aiMessage2 = response2.aiMessage();
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

    @Test
    void should_return_valid_json_object_using_model_large() {

        // given
        String userMessage = "Return JSON with two fields: transactionId and status with the values T123 and paid.";

        String expectedJson = "{\"transactionId\":\"T123\",\"status\":\"paid\"}";

        StreamingChatLanguageModel mistralLargeStreamingModel = MistralAiStreamingChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(MISTRAL_LARGE_LATEST)
                .temperature(0.1)
                .responseFormat(JSON_OBJECT)
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        mistralLargeStreamingModel.chat(userMessage, handler);
        String json = handler.get().aiMessage().text();

        // then
        assertThat(json).isEqualToIgnoringWhitespace(expectedJson);
    }

    @Test
    void bugfix_1218_allow_blank() {
        // given
        StreamingChatLanguageModel model = MistralAiStreamingChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(MISTRAL_SMALL_LATEST)
                .temperature(0d)
                .build();

        String userMessage = "What was inflation rate in germany in 2020? Answer in 1 short sentence. Begin your answer with 'In 2020, ...'";

        // when
        TestStreamingChatResponseHandler responseHandler = new TestStreamingChatResponseHandler();
        model.chat(userMessage, responseHandler);

        // results in: "In2020, Germany's inflation rate was0.5%."
        assertThat(responseHandler.get().aiMessage().text()).containsIgnoringCase("In 2020");
    }
}
