package dev.langchain4j.model.openaiofficial.openai;

import static dev.langchain4j.model.openaiofficial.openai.InternalOpenAiOfficialTestHelper.CHAT_MODEL_NAME_ALTERNATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.ChatModel;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCompletedEvent;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseError;
import com.openai.models.responses.ResponseFailedEvent;
import com.openai.models.responses.ResponseStatus;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.services.blocking.ResponseService;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatRequestParameters;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatResponseMetadata;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesChatRequestParameters;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesStreamingChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.InOrder;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiOfficialResponsesStreamingChatModelIT extends AbstractStreamingChatModelIT {

    @Override
    protected List<StreamingChatModel> models() {
        var client = OpenAIOkHttpClient.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();

        StreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                .client(client)
                .modelName("gpt-5-mini")
                .strict(true)
                .build();

        return List.of(model);
    }

    @Override
    protected StreamingChatModel createModelWith(ChatRequestParameters parameters) {
        var client = OpenAIOkHttpClient.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();

        OpenAiOfficialResponsesStreamingChatModel.Builder modelBuilder =
                OpenAiOfficialResponsesStreamingChatModel.builder()
                        .client(client)
                        .strict(true);

        if (parameters.modelName() != null) {
            modelBuilder.modelName(parameters.modelName());
        } else {
            modelBuilder.modelName(CHAT_MODEL_NAME_ALTERNATE.toString());
        }

        if (parameters instanceof OpenAiOfficialChatRequestParameters openAiParams) {
            if (openAiParams.temperature() != null) {
                modelBuilder.temperature(openAiParams.temperature());
            }
            if (openAiParams.topP() != null) {
                modelBuilder.topP(openAiParams.topP());
            }
            if (openAiParams.maxOutputTokens() != null) {
                modelBuilder.maxOutputTokens(openAiParams.maxOutputTokens());
            }
        }

        return modelBuilder.build();
    }

    @Override
    protected String customModelName() {
        return ChatModel.GPT_4O_2024_11_20.toString();
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        // Ensure minimum of 16 tokens for Responses API
        int effectiveMaxTokens = Math.max(maxOutputTokens, 16);
        return OpenAiOfficialChatRequestParameters.builder()
                .maxOutputTokens(effectiveMaxTokens)
                .build();
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(StreamingChatModel streamingChatModel) {
        return OpenAiOfficialChatResponseMetadata.class;
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(StreamingChatModel streamingChatModel) {
        return OpenAiOfficialTokenUsage.class;
    }

    @Override
    protected boolean supportsJsonResponseFormatWithRawSchema() {
        return false;
    }

    @Override
    public StreamingChatModel createModelWith(ChatModelListener listener) {
        var client = OpenAIOkHttpClient.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();

        return OpenAiOfficialResponsesStreamingChatModel.builder()
                .client(client)
                .modelName(InternalOpenAiOfficialTestHelper.CHAT_MODEL_NAME.toString())
                .strict(true)
                .listeners(listener)
                .build();
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id) {
        io.verify(handler).onCompleteToolCall(complete(0, id, "getWeather", "{\"city\":\"Munich\"}"));
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id1, String id2) {
        io.verify(handler).onCompleteToolCall(complete(0, id1, "getWeather", "{\"city\":\"Munich\"}"));
        io.verify(handler).onCompleteToolCall(complete(1, id2, "getTime", "{\"country\":\"France\"}"));
    }

    @Override
    protected void should_respect_modelName_in_chat_request(StreamingChatModel model) {
        // Responses API requires minimum of 16 tokens, override to use 16 instead of 1
        String modelName = customModelName();

        ChatRequestParameters parameters = ChatRequestParameters.builder()
                .modelName(modelName)
                .maxOutputTokens(16)
                .build();

        dev.langchain4j.model.chat.request.ChatRequest chatRequest =
                dev.langchain4j.model.chat.request.ChatRequest.builder()
                        .messages(UserMessage.from("Tell me a story"))
                        .parameters(parameters)
                        .build();

        dev.langchain4j.model.chat.response.ChatResponse chatResponse =
                chat(model, chatRequest).chatResponse();

        assertThat(chatResponse.aiMessage().text()).isNotBlank();
        assertThat(chatResponse.metadata().modelName()).isEqualTo(modelName);
    }

    @Override
    protected void should_respect_maxOutputTokens_in_chat_request(StreamingChatModel model) {
        // Responses API requires minimum of 16 tokens, so we use 16 instead of 5
        int maxOutputTokens = 16;
        ChatRequestParameters parameters =
                ChatRequestParameters.builder().maxOutputTokens(maxOutputTokens).build();
        dev.langchain4j.model.chat.request.ChatRequest chatRequest =
                dev.langchain4j.model.chat.request.ChatRequest.builder()
                        .messages(dev.langchain4j.data.message.UserMessage.from("Tell me a long story"))
                        .parameters(parameters)
                        .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(chatRequest, handler);
        dev.langchain4j.model.chat.response.ChatResponse chatResponse = handler.get();

        dev.langchain4j.data.message.AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).isNotBlank();
        assertThat(aiMessage.toolExecutionRequests()).isEmpty();

        if (assertTokenUsage()) {
            dev.langchain4j.model.output.TokenUsage tokenUsage =
                    chatResponse.metadata().tokenUsage();
            assertThat(tokenUsage).isExactlyInstanceOf(tokenUsageType(model));
            assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
            assertThat(tokenUsage.outputTokenCount()).isLessThanOrEqualTo(maxOutputTokens);
            assertThat(tokenUsage.totalTokenCount()).isGreaterThan(0);
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason())
                    .isEqualTo(dev.langchain4j.model.output.FinishReason.LENGTH);
        }
    }

    @Override
    protected void should_respect_maxOutputTokens_in_default_model_parameters() {
        // Responses API requires minimum of 16 tokens, so we use 16 instead of 5
        int maxOutputTokens = 16;
        ChatRequestParameters parameters =
                ChatRequestParameters.builder().maxOutputTokens(maxOutputTokens).build();

        StreamingChatModel model = createModelWith(parameters);
        if (model == null) {
            return;
        }

        dev.langchain4j.model.chat.request.ChatRequest chatRequest =
                dev.langchain4j.model.chat.request.ChatRequest.builder()
                        .messages(dev.langchain4j.data.message.UserMessage.from("Tell me a long story"))
                        .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(chatRequest, handler);
        dev.langchain4j.model.chat.response.ChatResponse chatResponse = handler.get();

        dev.langchain4j.data.message.AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).isNotBlank();
        assertThat(aiMessage.toolExecutionRequests()).isEmpty();

        if (assertTokenUsage()) {
            dev.langchain4j.model.output.TokenUsage tokenUsage =
                    chatResponse.metadata().tokenUsage();
            assertThat(tokenUsage).isExactlyInstanceOf(tokenUsageType(model));
            assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
            assertThat(tokenUsage.outputTokenCount()).isLessThanOrEqualTo(maxOutputTokens);
            assertThat(tokenUsage.totalTokenCount()).isGreaterThan(0);
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason())
                    .isEqualTo(dev.langchain4j.model.output.FinishReason.LENGTH);
        }
    }

    @Override
    protected void should_respect_common_parameters_wrapped_in_integration_specific_class_in_chat_request(
            StreamingChatModel model) {
        // Responses API requires minimum of 16 tokens, so we use 16 instead of 5
        int maxOutputTokens = 16;
        ChatRequestParameters parameters = createIntegrationSpecificParameters(maxOutputTokens);

        dev.langchain4j.model.chat.request.ChatRequest chatRequest =
                dev.langchain4j.model.chat.request.ChatRequest.builder()
                        .parameters(parameters)
                        .messages(dev.langchain4j.data.message.UserMessage.from("Tell me a long story"))
                        .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(chatRequest, handler);
        dev.langchain4j.model.chat.response.ChatResponse chatResponse = handler.get();

        dev.langchain4j.data.message.AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).isNotBlank();
        assertThat(aiMessage.toolExecutionRequests()).isEmpty();

        if (assertTokenUsage()) {
            dev.langchain4j.model.output.TokenUsage tokenUsage =
                    chatResponse.metadata().tokenUsage();
            assertThat(tokenUsage).isExactlyInstanceOf(tokenUsageType(model));
            assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
            assertThat(tokenUsage.outputTokenCount()).isLessThanOrEqualTo(maxOutputTokens);
            assertThat(tokenUsage.totalTokenCount()).isGreaterThan(0);
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason())
                    .isEqualTo(dev.langchain4j.model.output.FinishReason.LENGTH);
        }
    }

    @Override
    @Disabled("Responses API does not support stop sequences")
    protected void should_respect_stopSequences_in_chat_request(StreamingChatModel model) {}

    @Override
    @Disabled("Responses API does not support stop sequences")
    protected void should_respect_stopSequences_in_default_model_parameters() {}

    @Override
    protected void
            should_respect_common_parameters_wrapped_in_integration_specific_class_in_default_model_parameters() {
        // Responses API requires minimum of 16 tokens, so we use 16 instead of 5
        int maxOutputTokens = 16;
        ChatRequestParameters parameters = createIntegrationSpecificParameters(maxOutputTokens);

        StreamingChatModel model = createModelWith(parameters);

        dev.langchain4j.model.chat.request.ChatRequest chatRequest =
                dev.langchain4j.model.chat.request.ChatRequest.builder()
                        .parameters(parameters)
                        .messages(dev.langchain4j.data.message.UserMessage.from("Tell me a long story"))
                        .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(chatRequest, handler);
        dev.langchain4j.model.chat.response.ChatResponse chatResponse = handler.get();

        dev.langchain4j.data.message.AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).isNotBlank();
        assertThat(aiMessage.toolExecutionRequests()).isEmpty();

        if (assertTokenUsage()) {
            dev.langchain4j.model.output.TokenUsage tokenUsage =
                    chatResponse.metadata().tokenUsage();
            assertThat(tokenUsage).isExactlyInstanceOf(tokenUsageType(model));
            assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
            assertThat(tokenUsage.outputTokenCount()).isLessThanOrEqualTo(maxOutputTokens);
            assertThat(tokenUsage.totalTokenCount()).isGreaterThan(0);
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason())
                    .isEqualTo(dev.langchain4j.model.output.FinishReason.LENGTH);
        }
    }

    @Test
    void should_work_with_o_models() {

        // given
        var client = OpenAIOkHttpClient.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();

        StreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                .client(client)
                .modelName("o4-mini")
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("What is the capital of Germany?", handler);

        // then
        var response = handler.get();
        assertThat(response.aiMessage().text()).contains("Berlin");
        assertThat(response.metadata()).isInstanceOf(OpenAiOfficialChatResponseMetadata.class);
        OpenAiOfficialChatResponseMetadata metadata = (OpenAiOfficialChatResponseMetadata) response.metadata();
        assertThat(metadata.id()).isNotBlank();
        assertThat(metadata.modelName()).isNotBlank();
        assertThat(metadata.finishReason()).isNotNull();
    }

    @Test
    void should_support_strict_mode_false() {

        // given
        var client = OpenAIOkHttpClient.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();

        StreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                .client(client)
                .modelName(InternalOpenAiOfficialTestHelper.CHAT_MODEL_NAME.toString())
                .strict(false)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("What is 2+2?", handler);

        // then
        var response = handler.get();
        assertThat(response.aiMessage().text()).isNotBlank();
        assertThat(response.metadata()).isInstanceOf(OpenAiOfficialChatResponseMetadata.class);
        OpenAiOfficialChatResponseMetadata metadata = (OpenAiOfficialChatResponseMetadata) response.metadata();
        assertThat(metadata.id()).isNotBlank();
        assertThat(metadata.modelName()).isNotBlank();
        assertThat(metadata.finishReason()).isNotNull();
    }

    @Test
    void should_support_reasoning_effort() {

        // given
        var client = OpenAIOkHttpClient.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();

        StreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                .client(client)
                .modelName("o4-mini")
                .reasoningEffort("medium")
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("What is the capital of France?", handler);

        // then
        var response = handler.get();
        assertThat(response.aiMessage().text()).contains("Paris");
        assertThat(response.metadata()).isInstanceOf(OpenAiOfficialChatResponseMetadata.class);
        OpenAiOfficialChatResponseMetadata metadata = (OpenAiOfficialChatResponseMetadata) response.metadata();
        assertThat(metadata.id()).isNotBlank();
        assertThat(metadata.modelName()).isNotBlank();
        assertThat(metadata.finishReason()).isNotNull();
    }

    @Test
    void should_support_max_tool_calls() {

        // given
        var client = OpenAIOkHttpClient.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();

        StreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                .client(client)
                .modelName(InternalOpenAiOfficialTestHelper.CHAT_MODEL_NAME.toString())
                .maxToolCalls(1)
                .build();

        // when
        ToolSpecification weatherTool = ToolSpecification.builder()
                .name("getWeather")
                .parameters(JsonObjectSchema.builder().addStringProperty("city").build())
                .build();
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Use getWeather to report weather in Munich"))
                .toolSpecifications(weatherTool)
                .toolChoice(ToolChoice.REQUIRED)
                .build();
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(chatRequest, handler);

        // then
        var response = handler.get();
        assertThat(response.aiMessage().toolExecutionRequests()).hasSize(1);
        String toolName = response.aiMessage().toolExecutionRequests().get(0).name();
        assertThat(toolName).isEqualTo("getWeather");
    }

    @Test
    void should_support_parallel_tool_calls_disabled() {

        // given
        var client = OpenAIOkHttpClient.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();

        StreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                .client(client)
                .modelName(InternalOpenAiOfficialTestHelper.CHAT_MODEL_NAME.toString())
                .parallelToolCalls(false)
                .build();

        // when
        ToolSpecification weatherTool = ToolSpecification.builder()
                .name("getWeather")
                .parameters(JsonObjectSchema.builder().addStringProperty("city").build())
                .build();
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Use getWeather to report weather in Munich"))
                .toolSpecifications(weatherTool)
                .toolChoice(ToolChoice.REQUIRED)
                .build();
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(chatRequest, handler);

        // then
        var response = handler.get();
        assertThat(response.aiMessage().toolExecutionRequests()).hasSize(1);
    }

    @Test
    void should_send_previous_response_id_from_builder() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        ResponseCreateParams[] capturedParams = new ResponseCreateParams[1];
        var executor = Executors.newSingleThreadExecutor();

        OpenAIClient client = mock(OpenAIClient.class);
        ResponseService responseService = mock(ResponseService.class);
        @SuppressWarnings("unchecked")
        StreamResponse<ResponseStreamEvent> streamResponse = mock(StreamResponse.class);
        when(client.responses()).thenReturn(responseService);
        when(responseService.createStreaming(any(ResponseCreateParams.class))).thenAnswer(invocation -> {
            capturedParams[0] = invocation.getArgument(0);
            latch.countDown();
            return streamResponse;
        });
        when(streamResponse.stream()).thenReturn(java.util.stream.Stream.empty());

        StreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                .client(client)
                .executorService(executor)
                .modelName("gpt-5-mini")
                .previousResponseId("builder-response-id")
                .build();

        model.chat("Hello", new TestStreamingChatResponseHandler());

        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(capturedParams[0].previousResponseId()).contains("builder-response-id");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void should_send_previous_response_id_from_request_parameters() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        ResponseCreateParams[] capturedParams = new ResponseCreateParams[1];
        var executor = Executors.newSingleThreadExecutor();

        OpenAIClient client = mock(OpenAIClient.class);
        ResponseService responseService = mock(ResponseService.class);
        @SuppressWarnings("unchecked")
        StreamResponse<ResponseStreamEvent> streamResponse = mock(StreamResponse.class);
        when(client.responses()).thenReturn(responseService);
        when(responseService.createStreaming(any(ResponseCreateParams.class))).thenAnswer(invocation -> {
            capturedParams[0] = invocation.getArgument(0);
            latch.countDown();
            return streamResponse;
        });
        when(streamResponse.stream()).thenReturn(java.util.stream.Stream.empty());

        StreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                .client(client)
                .executorService(executor)
                .modelName("gpt-5-mini")
                .previousResponseId("builder-response-id")
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .parameters(OpenAiOfficialResponsesChatRequestParameters.builder()
                        .previousResponseId("request-response-id")
                        .build())
                .build();
        model.chat(chatRequest, new TestStreamingChatResponseHandler());

        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(capturedParams[0].previousResponseId()).contains("request-response-id");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void should_map_completed_status_to_finish_reason_stop() {
        @SuppressWarnings("unchecked")
        StreamResponse<ResponseStreamEvent> streamResponse = mock(StreamResponse.class);
        ResponseStreamEvent streamEvent = mock(ResponseStreamEvent.class);
        ResponseCompletedEvent completedEvent = mock(ResponseCompletedEvent.class);
        Response response = mock(Response.class);

        when(streamEvent.isCompleted()).thenReturn(true);
        when(streamEvent.asCompleted()).thenReturn(completedEvent);
        when(completedEvent.response()).thenReturn(response);
        when(response.status()).thenReturn(Optional.of(ResponseStatus.COMPLETED));
        when(response.usage()).thenReturn(Optional.empty());
        when(streamResponse.stream()).thenReturn(java.util.stream.Stream.of(streamEvent));

        OpenAIClient client = mock(OpenAIClient.class);
        ResponseService responseService = mock(ResponseService.class);
        when(client.responses()).thenReturn(responseService);
        when(responseService.createStreaming(any(ResponseCreateParams.class))).thenReturn(streamResponse);

        var executor = Executors.newSingleThreadExecutor();
        try {
            StreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                    .client(client)
                    .executorService(executor)
                    .modelName("gpt-5-mini")
                    .build();

            TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
            model.chat("Hello", handler);

            assertThat(handler.get().metadata().finishReason())
                    .isEqualTo(dev.langchain4j.model.output.FinishReason.STOP);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void should_surface_failed_event_error_message_without_optional_wrapper() {
        @SuppressWarnings("unchecked")
        StreamResponse<ResponseStreamEvent> streamResponse = mock(StreamResponse.class);
        ResponseStreamEvent streamEvent = mock(ResponseStreamEvent.class);
        ResponseFailedEvent failedEvent = mock(ResponseFailedEvent.class);
        Response response = mock(Response.class);
        ResponseError error = mock(ResponseError.class);

        when(streamEvent.isFailed()).thenReturn(true);
        when(streamEvent.asFailed()).thenReturn(failedEvent);
        when(failedEvent.response()).thenReturn(response);
        when(response.error()).thenReturn(Optional.of(error));
        when(error.message()).thenReturn("tokens exceeded");
        when(streamResponse.stream()).thenReturn(java.util.stream.Stream.of(streamEvent));

        OpenAIClient client = mock(OpenAIClient.class);
        ResponseService responseService = mock(ResponseService.class);
        when(client.responses()).thenReturn(responseService);
        when(responseService.createStreaming(any(ResponseCreateParams.class))).thenReturn(streamResponse);

        var executor = Executors.newSingleThreadExecutor();
        try {
            StreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                    .client(client)
                    .executorService(executor)
                    .modelName("gpt-5-mini")
                    .build();

            TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
            model.chat("Hello", handler);

            assertThatThrownBy(handler::get).rootCause().hasMessage("Response failed: tokens exceeded");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void should_fallback_to_error_to_string_when_failed_event_message_is_blank() {
        @SuppressWarnings("unchecked")
        StreamResponse<ResponseStreamEvent> streamResponse = mock(StreamResponse.class);
        ResponseStreamEvent streamEvent = mock(ResponseStreamEvent.class);
        ResponseFailedEvent failedEvent = mock(ResponseFailedEvent.class);
        Response response = mock(Response.class);
        ResponseError error = mock(ResponseError.class);

        when(streamEvent.isFailed()).thenReturn(true);
        when(streamEvent.asFailed()).thenReturn(failedEvent);
        when(failedEvent.response()).thenReturn(response);
        when(response.error()).thenReturn(Optional.of(error));
        when(error.message()).thenReturn(" ");
        when(error.toString()).thenReturn("server_error");
        when(streamResponse.stream()).thenReturn(java.util.stream.Stream.of(streamEvent));

        OpenAIClient client = mock(OpenAIClient.class);
        ResponseService responseService = mock(ResponseService.class);
        when(client.responses()).thenReturn(responseService);
        when(responseService.createStreaming(any(ResponseCreateParams.class))).thenReturn(streamResponse);

        var executor = Executors.newSingleThreadExecutor();
        try {
            StreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                    .client(client)
                    .executorService(executor)
                    .modelName("gpt-5-mini")
                    .build();

            TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
            model.chat("Hello", handler);

            assertThatThrownBy(handler::get).rootCause().hasMessage("Response failed: server_error");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void should_map_incomplete_status_to_finish_reason_length() {
        @SuppressWarnings("unchecked")
        StreamResponse<ResponseStreamEvent> streamResponse = mock(StreamResponse.class);
        ResponseStreamEvent streamEvent = mock(ResponseStreamEvent.class);
        com.openai.models.responses.ResponseIncompleteEvent incompleteEvent =
                mock(com.openai.models.responses.ResponseIncompleteEvent.class);
        Response response = mock(Response.class);

        when(streamEvent.isIncomplete()).thenReturn(true);
        when(streamEvent.asIncomplete()).thenReturn(incompleteEvent);
        when(incompleteEvent.response()).thenReturn(response);
        when(response.usage()).thenReturn(Optional.empty());
        when(streamResponse.stream()).thenReturn(java.util.stream.Stream.of(streamEvent));

        OpenAIClient client = mock(OpenAIClient.class);
        ResponseService responseService = mock(ResponseService.class);
        when(client.responses()).thenReturn(responseService);
        when(responseService.createStreaming(any(ResponseCreateParams.class))).thenReturn(streamResponse);

        var executor = Executors.newSingleThreadExecutor();
        try {
            StreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                    .client(client)
                    .executorService(executor)
                    .modelName("gpt-5-mini")
                    .build();

            TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
            model.chat("Hello", handler);

            assertThat(handler.get().metadata().finishReason())
                    .isEqualTo(dev.langchain4j.model.output.FinishReason.LENGTH);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void should_not_call_on_complete_response_after_cancellation() throws Exception {
        @SuppressWarnings("unchecked")
        StreamResponse<ResponseStreamEvent> streamResponse = mock(StreamResponse.class);
        ResponseStreamEvent deltaEvent = mock(ResponseStreamEvent.class);
        ResponseStreamEvent completedStreamEvent = mock(ResponseStreamEvent.class);
        com.openai.models.responses.ResponseTextDeltaEvent textDeltaEvent =
                mock(com.openai.models.responses.ResponseTextDeltaEvent.class);
        ResponseCompletedEvent completedEvent = mock(ResponseCompletedEvent.class);
        Response response = mock(Response.class);

        when(deltaEvent.isOutputTextDelta()).thenReturn(true);
        when(deltaEvent.asOutputTextDelta()).thenReturn(textDeltaEvent);
        when(textDeltaEvent.delta()).thenReturn("Hello");

        when(completedStreamEvent.isCompleted()).thenReturn(true);
        when(completedStreamEvent.asCompleted()).thenReturn(completedEvent);
        when(completedEvent.response()).thenReturn(response);
        when(response.status()).thenReturn(Optional.of(ResponseStatus.COMPLETED));
        when(response.usage()).thenReturn(Optional.empty());

        when(streamResponse.stream()).thenReturn(java.util.stream.Stream.of(deltaEvent, completedStreamEvent));

        OpenAIClient client = mock(OpenAIClient.class);
        ResponseService responseService = mock(ResponseService.class);
        when(client.responses()).thenReturn(responseService);
        when(responseService.createStreaming(any(ResponseCreateParams.class))).thenReturn(streamResponse);

        var executor = Executors.newSingleThreadExecutor();
        try {
            StreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                    .client(client)
                    .executorService(executor)
                    .modelName("gpt-5-mini")
                    .build();

            CountDownLatch partialLatch = new CountDownLatch(1);
            CountDownLatch completeLatch = new CountDownLatch(1);
            AtomicInteger completeCount = new AtomicInteger();

            model.chat("Hello", new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
                    context.streamingHandle().cancel();
                    partialLatch.countDown();
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    completeCount.incrementAndGet();
                    completeLatch.countDown();
                }

                @Override
                public void onError(Throwable error) {
                    throw new AssertionError("onError must not be called", error);
                }
            });

            assertThat(partialLatch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(completeLatch.await(300, TimeUnit.MILLISECONDS)).isFalse();
            assertThat(completeCount).hasValue(0);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void should_fail_when_input_exceeds_context_limit() {

        // given
        var client = OpenAIOkHttpClient.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();

        StreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                .client(client)
                .modelName("o4-mini")
                .build();

        String largeInput = generateLargeInput();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(largeInput, handler);

        // then
        assertThatThrownBy(handler::get)
                .isInstanceOf(RuntimeException.class)
                .hasRootCauseInstanceOf(com.openai.errors.SseException.class)
                .hasRootCauseMessage(
                        "200: Your input exceeds the context window of this model. Please adjust your input and try again.");
    }

    private static String generateLargeInput() {
        StringBuilder builder = new StringBuilder(650_000);
        builder.append("count 'a' characters below:\n");
        for (int i = 0; i < 400_000; i++) {
            builder.append("a ");
        }
        return builder.toString();
    }

    @Override
    protected boolean supportsSingleImageInputAsPublicURL() {
        return false;
    }

    @Override
    protected boolean supportsMultipleImageInputsAsPublicURLs() {
        return false;
    }

    @Override
    protected void should_fail_if_images_as_public_URLs_are_not_supported(StreamingChatModel model) {

        // given
        UserMessage userMessage =
                UserMessage.from(TextContent.from("What do you see?"), ImageContent.from(catImageUrl()));
        dev.langchain4j.model.chat.request.ChatRequest chatRequest =
                dev.langchain4j.model.chat.request.ChatRequest.builder()
                        .messages(userMessage)
                        .build();

        // when-then
        assertThatThrownBy(() -> chat(model, chatRequest));
    }

    @Override
    @Disabled("Can't do it reliably")
    protected void should_execute_multiple_tools_in_parallel_then_answer(StreamingChatModel model) {}
}
