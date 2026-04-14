package dev.langchain4j.model.openaiofficial.openai;

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
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatRequestParameters;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesChatRequestParameters;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesChatResponseMetadata;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesStreamingChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiOfficialResponsesStreamingChatModelIT extends AbstractStreamingChatModelIT {

    private static final String GPT_5_4_MINI = "gpt-5.4-mini";
    private static final int MAX_OUTPUT_TOKENS_MIN_VALUE = 16;

    @Override
    protected List<StreamingChatModel> models() {
        StreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(GPT_5_4_MINI)
                .build();

        return List.of(model);
    }

    @Override
    protected StreamingChatModel createModelWith(ChatRequestParameters parameters) {
        return OpenAiOfficialResponsesStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .defaultRequestParameters(parameters)
                .modelName(getOrDefault(parameters.modelName(), "gpt-5.4-mini"))
                .build();
    }

    @Override
    protected String customModelName() {
        return ChatModel.GPT_4O_2024_11_20.toString();
    }

    @Override
    protected int maxOutputTokens() {
        return MAX_OUTPUT_TOKENS_MIN_VALUE;
    }

    @Override
    protected ChatRequestParameters saveTokens(ChatRequestParameters parameters) {
        return parameters.overrideWith(ChatRequestParameters.builder()
                .maxOutputTokens(MAX_OUTPUT_TOKENS_MIN_VALUE).build());
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return OpenAiOfficialChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(StreamingChatModel model) {
        return OpenAiOfficialResponsesChatResponseMetadata.class;
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(StreamingChatModel model) {
        return OpenAiOfficialTokenUsage.class;
    }

    @Override
    protected boolean supportsJsonResponseFormatWithRawSchema() {
        return false; // TODO
    }

    @Override
    public StreamingChatModel createModelWith(ChatModelListener listener) {
        return OpenAiOfficialResponsesStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-5.4-mini")
                .listeners(listener)
                .build();
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id) {
        io.verify(handler, atLeast(1)).onPartialToolCall(argThat(toolCall ->
                toolCall.index() == 0
                        && toolCall.id().equals(id)
                        && toolCall.name().equals("getWeather")
                        && !toolCall.partialArguments().isBlank()
        ), any());
        io.verify(handler).onCompleteToolCall(argThat(toolCall ->
                {
                    ToolExecutionRequest request = toolCall.toolExecutionRequest();
                    return toolCall.index() == 0
                            && request.id().equals(id)
                            && request.name().equals("getWeather")
                            && request.arguments().replace(" ", "").equals("{\"city\":\"Munich\"}");
                }
        ));
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id1, String id2) {
        io.verify(handler, atLeast(1)).onPartialToolCall(argThat(toolCall ->
                toolCall.index() == 0
                        && toolCall.id().equals(id1)
                        && toolCall.name().equals("getWeather")
                        && !toolCall.partialArguments().isBlank()
        ), any());
        io.verify(handler).onCompleteToolCall(argThat(toolCall ->
                {
                    ToolExecutionRequest request = toolCall.toolExecutionRequest();
                    return toolCall.index() == 0
                            && request.id().equals(id1)
                            && request.name().equals("getWeather")
                            && request.arguments().replace(" ", "").equals("{\"city\":\"Munich\"}");
                }
        ));

        io.verify(handler, atLeast(1)).onPartialToolCall(argThat(toolCall ->
                toolCall.index() == 1
                        && toolCall.id().equals(id2)
                        && toolCall.name().equals("getTime")
                        && !toolCall.partialArguments().isBlank()
        ), any());
        io.verify(handler).onCompleteToolCall(argThat(toolCall ->
                {
                    ToolExecutionRequest request = toolCall.toolExecutionRequest();
                    return toolCall.index() == 1
                            && request.id().equals(id2)
                            && request.name().equals("getTime")
                            && request.arguments().replace(" ", "").equals("{\"country\":\"France\"}");
                }
        ));
    }

    @Override
    protected boolean supportsStopSequencesParameter() {
        return false;
    }

    // TODO review tests below

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
        assertThat(response.metadata()).isInstanceOf(OpenAiOfficialResponsesChatResponseMetadata.class);
        OpenAiOfficialResponsesChatResponseMetadata metadata = (OpenAiOfficialResponsesChatResponseMetadata) response.metadata();
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
        assertThat(response.metadata()).isInstanceOf(OpenAiOfficialResponsesChatResponseMetadata.class);
        OpenAiOfficialResponsesChatResponseMetadata metadata = (OpenAiOfficialResponsesChatResponseMetadata) response.metadata();
        assertThat(metadata.id()).isNotBlank();
        assertThat(metadata.modelName()).isNotBlank();
        assertThat(metadata.finishReason()).isNotNull();
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
}
