package dev.langchain4j.model.dashscope;

import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.listener.*;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.INTEGER;
import static dev.langchain4j.data.message.ToolExecutionResultMessage.from;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.dashscope.QwenTestHelper.*;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

        assertThat(response.content().text()).containsIgnoringCase("rain");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#functionCallChatModelNameProvider")
    public void should_call_function_with_no_argument_then_answer(String modelName) {
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

        Response<AiMessage> response = model.generate(singletonList(userMessage), singletonList(noArgToolSpec));

        assertThat(response.content().text()).isNull();
        assertThat(response.content().toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest = response.content().toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(toolName);
        assertThat(toolExecutionRequest.arguments()).isEqualTo("{}");
        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "10 o'clock");
        List<ChatMessage> messages = asList(userMessage, response.content(), toolExecutionResultMessage);

        Response<AiMessage> secondResponse = model.generate(messages, singletonList(noArgToolSpec));

        AiMessage secondAiMessage = secondResponse.content();
        assertThat(secondAiMessage.text()).contains("10");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();

        TokenUsage secondTokenUsage = secondResponse.tokenUsage();
        assertThat(secondTokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(secondTokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(secondTokenUsage.totalTokenCount())
                .isEqualTo(secondTokenUsage.inputTokenCount() + secondTokenUsage.outputTokenCount());
        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#functionCallChatModelNameProvider")
    public void should_call_function_with_argument_then_answer(String modelName) {
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

        Response<AiMessage> response = model.generate(singletonList(userMessage), singletonList(hasArgToolSpec));

        assertThat(response.content().text()).isNull();
        assertThat(response.content().toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest = response.content().toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(toolName);
        assertThat(toolExecutionRequest.arguments()).contains("Beijing");
        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "rainy");
        List<ChatMessage> messages = asList(userMessage, response.content(), toolExecutionResultMessage);

        Response<AiMessage> secondResponse = model.generate(messages, singletonList(hasArgToolSpec));

        AiMessage secondAiMessage = secondResponse.content();
        assertThat(secondAiMessage.text()).contains("rain");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();

        TokenUsage secondTokenUsage = secondResponse.tokenUsage();
        assertThat(secondTokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(secondTokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(secondTokenUsage.totalTokenCount())
                .isEqualTo(secondTokenUsage.inputTokenCount() + secondTokenUsage.outputTokenCount());
        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
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

        Response<AiMessage> response = model.generate(singletonList(userMessage), mustBeExecutedTool);

        assertThat(response.content().text()).isNull();
        assertThat(response.content().toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest = response.content().toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(toolName);
        assertThat(toolExecutionRequest.arguments()).hasSizeGreaterThan(0);
        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#functionCallChatModelNameProvider")
    void should_call_must_be_executed_call_function_with_argument_then_answer(String modelName) {
        ChatLanguageModel model = QwenChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();

        String toolName = "calculator";
        ToolSpecification calculator = ToolSpecification.builder()
                .name(toolName)
                .description("returns a sum of two numbers")
                .addParameter("first", INTEGER)
                .addParameter("second", INTEGER)
                .build();

        UserMessage userMessage = userMessage("2+2=?");

        Response<AiMessage> response = model.generate(singletonList(userMessage), calculator);

        AiMessage aiMessage = response.content();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.id()).isNotNull();
        assertThat(toolExecutionRequest.name()).isEqualTo(toolName);
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "4");
        List<ChatMessage> messages = asList(userMessage, aiMessage, toolExecutionResultMessage);

        Response<AiMessage> secondResponse = model.generate(messages, singletonList(calculator));

        AiMessage secondAiMessage = secondResponse.content();
        assertThat(secondAiMessage.text()).contains("4");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();

        TokenUsage secondTokenUsage = secondResponse.tokenUsage();
        assertThat(secondTokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(secondTokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(secondTokenUsage.totalTokenCount())
                .isEqualTo(secondTokenUsage.inputTokenCount() + secondTokenUsage.outputTokenCount());
        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#vlChatModelNameProvider")
    public void should_send_multimodal_image_url_and_receive_response(String modelName) {
        ChatLanguageModel model = QwenChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();

        Response<AiMessage> response = model.generate(multimodalChatMessagesWithImageUrl());

        assertThat(response.content().text()).containsIgnoringCase("dog");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#vlChatModelNameProvider")
    public void should_send_multimodal_image_data_and_receive_response(String modelName) {
        ChatLanguageModel model = QwenChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();

        Response<AiMessage> response = model.generate(multimodalChatMessagesWithImageData());

        assertThat(response.content().text()).containsIgnoringCase("parrot");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#audioChatModelNameProvider")
    public void should_send_multimodal_audio_url_and_receive_response(String modelName) {
        ChatLanguageModel model = QwenChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();

        Response<AiMessage> response = model.generate(multimodalChatMessagesWithAudioUrl());

        assertThat(response.content().text()).containsIgnoringCase("阿里云");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#audioChatModelNameProvider")
    public void should_send_multimodal_audio_data_and_receive_response(String modelName) {
        ChatLanguageModel model = QwenChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();

        Response<AiMessage> response = model.generate(multimodalChatMessagesWithAudioData());

        assertThat(response.content().text()).containsIgnoringCase("阿里云");
    }

    @Test
    public void should_sanitize_messages() {
        List<ChatMessage> messages = new LinkedList<>();

        // 1. The system message should be the first message.
        // 2. User/Tool-execution-result messages and AI messages should alternate.
        // 3. The last message in the message list should be a user message. This serves as the model query/input for the current round.

        messages.add(SystemMessage.from("System message 1, which should be discarded"));
        messages.add(UserMessage.from("User message 1, which should be discarded"));
        messages.add(SystemMessage.from("System message 2"));

        messages.add(AiMessage.from("AI message 1, which should be discarded"));
        messages.add(ToolExecutionResultMessage.from(ToolExecutionRequest.builder().build(),
                "Tool execution result 1, which should be discards"));
        messages.add(UserMessage.from("User message 2, which should be discarded"));
        messages.add(UserMessage.from("User message 3"));

        messages.add(AiMessage.from("AI message 2, which should be discarded"));
        messages.add(AiMessage.from("AI message 3"));

        messages.add(ToolExecutionResultMessage.from(ToolExecutionRequest.builder().build(),
                "Tool execution result 2, which should be discards"));
        messages.add(ToolExecutionResultMessage.from(ToolExecutionRequest.builder().build(),
                "Tool execution result 3"));

        messages.add(AiMessage.from("AI message 4"));

        messages.add(UserMessage.from("User message 4, which should be discards"));
        messages.add(UserMessage.from("User message 5"));

        messages.add(AiMessage.from("AI message 5, which should be discards"));

        // The result should be in the following order:
        // 1. System message
        // 2. User message
        // 3. AI message
        // 4. Tool execution result message
        // 5. AI message
        // 6. User message
        List<ChatMessage> sanitizedMessages = QwenHelper.sanitizeMessages(messages);
        assertThat(sanitizedMessages).hasSize(6);

        assertThat(sanitizedMessages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(((SystemMessage) sanitizedMessages.get(0)).text()).isEqualTo("System message 2");

        assertThat(sanitizedMessages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) sanitizedMessages.get(1)).singleText()).isEqualTo("User message 3");

        assertThat(sanitizedMessages.get(2)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) sanitizedMessages.get(2)).text()).isEqualTo("AI message 3");

        assertThat(sanitizedMessages.get(3)).isInstanceOf(ToolExecutionResultMessage.class);
        assertThat(((ToolExecutionResultMessage) sanitizedMessages.get(3)).text()).isEqualTo("Tool execution result 3");

        assertThat(sanitizedMessages.get(4)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) sanitizedMessages.get(4)).text()).isEqualTo("AI message 4");

        assertThat(sanitizedMessages.get(5)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) sanitizedMessages.get(5)).singleText()).isEqualTo("User message 5");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#listenableModelNameProvider")
    void should_listen_request_and_response(String modelName, boolean supportTools) {

        // given
        AtomicReference<ChatModelRequest> requestReference = new AtomicReference<>();
        AtomicReference<ChatModelResponse> responseReference = new AtomicReference<>();

        ChatModelListener listener = new ChatModelListener() {

            @Override
            public void onRequest(ChatModelRequestContext requestContext) {
                requestReference.set(requestContext.request());
                requestContext.attributes().put("id", "12345");
            }

            @Override
            public void onResponse(ChatModelResponseContext responseContext) {
                responseReference.set(responseContext.response());
                assertThat(responseContext.request()).isSameAs(requestReference.get());
                assertThat(responseContext.attributes().get("id")).isEqualTo("12345");
            }

            @Override
            public void onError(ChatModelErrorContext errorContext) {
                fail("onError() must not be called");
            }
        };

        float temperature = 0.7f;
        double topP = 1.0;
        int maxTokens = 7;

        ChatLanguageModel model = QwenChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .temperature(temperature)
                .topP(topP)
                .maxTokens(maxTokens)
                .listeners(singletonList(listener))
                .build();

        UserMessage userMessage = UserMessage.from("hello");

        ToolSpecification toolSpecification = ToolSpecification.builder()
                .description("adds two numbers")
                .name("add")
                .addParameter("a", INTEGER)
                .addParameter("b", INTEGER)
                .build();

        // when
        AiMessage aiMessage = supportTools ?
                model.generate(singletonList(userMessage), singletonList(toolSpecification)).content() :
                model.generate(singletonList(userMessage)).content();

        // then
        ChatModelRequest request = requestReference.get();
        assertThat(request.model()).isEqualTo(modelName);
        assertThat(request.temperature()).isEqualTo(temperature);
        assertThat(request.topP()).isEqualTo(topP);
        assertThat(request.maxTokens()).isEqualTo(maxTokens);
        assertThat(request.messages()).containsExactly(userMessage);
        if (supportTools) {
            assertThat(request.toolSpecifications()).containsExactly(toolSpecification);
        }

        ChatModelResponse response = responseReference.get();
        assertThat(response.id()).isNotBlank();
        assertThat(response.model()).isNotBlank();
        assertThat(response.tokenUsage().inputTokenCount()).isGreaterThan(0);
        assertThat(response.tokenUsage().outputTokenCount()).isGreaterThan(0);
        assertThat(response.tokenUsage().totalTokenCount()).isGreaterThan(0);
        assertThat(response.finishReason()).isNotNull();
        assertThat(response.aiMessage()).isEqualTo(aiMessage);
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#listenableModelNameProvider")
    void should_listen_error(String modelName) {

        // given
        String wrongApiKey = "banana";

        AtomicReference<ChatModelRequest> requestReference = new AtomicReference<>();
        AtomicReference<Throwable> errorReference = new AtomicReference<>();

        ChatModelListener listener = new ChatModelListener() {

            @Override
            public void onRequest(ChatModelRequestContext requestContext) {
                requestReference.set(requestContext.request());
                requestContext.attributes().put("id", "12345");
            }

            @Override
            public void onResponse(ChatModelResponseContext responseContext) {
                fail("onResponse() must not be called");
            }

            @Override
            public void onError(ChatModelErrorContext errorContext) {
                errorReference.set(errorContext.error());
                assertThat(errorContext.request()).isSameAs(requestReference.get());
                assertThat(errorContext.partialResponse()).isNull();
                assertThat(errorContext.attributes().get("id")).isEqualTo("12345");
            }
        };

        ChatLanguageModel model = QwenChatModel.builder()
                .apiKey(wrongApiKey)
                .modelName(modelName)
                .listeners(singletonList(listener))
                .build();

        String userMessage = "this message will fail";

        // when
        assertThrows(RuntimeException.class, () -> model.generate(userMessage));

        // then
        Throwable throwable = errorReference.get();
        assertThat(throwable).isExactlyInstanceOf(com.alibaba.dashscope.exception.ApiException.class);
        assertThat(throwable).hasMessageContaining("Invalid API-key provided");
    }
}
