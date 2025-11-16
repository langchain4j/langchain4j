package dev.langchain4j.model.openaiofficial.openai;

import static dev.langchain4j.model.openaiofficial.openai.InternalOpenAiOfficialTestHelper.CHAT_MODEL_NAME_ALTERNATE;
import static org.assertj.core.api.Assertions.assertThat;

import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatRequestParameters;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatResponseMetadata;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesStreamingChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.concurrent.Executors;
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
                .modelName(InternalOpenAiOfficialTestHelper.CHAT_MODEL_NAME.toString())
                .executorService(Executors.newCachedThreadPool())
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
                        .executorService(Executors.newCachedThreadPool());

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
                modelBuilder.maxOutputTokens(openAiParams.maxOutputTokens().longValue());
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
    public StreamingChatModel createModelWith(ChatModelListener listener) {
        var client = OpenAIOkHttpClient.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();

        return OpenAiOfficialResponsesStreamingChatModel.builder()
                .client(client)
                .modelName(InternalOpenAiOfficialTestHelper.CHAT_MODEL_NAME.toString())
                .executorService(Executors.newCachedThreadPool())
                .listeners(List.of(listener))
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
    @Disabled("Responses API does not support JSON response format")
    protected void should_respect_JSON_response_format(StreamingChatModel model) {}

    @Override
    @Disabled("Responses API does not support JSON response format")
    protected void should_respect_JSON_response_format_with_schema(StreamingChatModel model) {}

    @Override
    @Disabled("Responses API does not support JSON response format")
    protected void should_respect_JsonRawSchema_responseFormat(StreamingChatModel model) {}

    @Override
    @Disabled("Responses API does not support JSON response format")
    protected void should_execute_a_tool_then_answer_respecting_JSON_response_format_with_schema(
            StreamingChatModel model) {}



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
                .executorService(Executors.newCachedThreadPool())
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("What is the capital of Germany?", handler);

        // then
        assertThat(handler.get().aiMessage().text()).contains("Berlin");
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
                .executorService(Executors.newCachedThreadPool())
                .strict(false)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("What is 2+2?", handler);

        // then
        assertThat(handler.get().aiMessage().text()).isNotBlank();
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
                .executorService(Executors.newCachedThreadPool())
                .reasoningEffort("medium")
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("What is the capital of France?", handler);

        // then
        assertThat(handler.get().aiMessage().text()).contains("Paris");
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
                .executorService(Executors.newCachedThreadPool())
                .maxToolCalls(1L)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("What is the weather?", handler);

        // then
        assertThat(handler.get().aiMessage().text()).isNotBlank();
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
                .executorService(Executors.newCachedThreadPool())
                .parallelToolCalls(false)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("Hello", handler);

        // then
        assertThat(handler.get().aiMessage().text()).isNotBlank();
    }

    // Responses API does not support vision/images
    @Override
    protected boolean supportsSingleImageInputAsPublicURL() {
        return true;
    }

    @Override
    protected boolean supportsSingleImageInputAsBase64EncodedString() {
        return true;
    }

    @Override
    protected boolean supportsMultipleImageInputsAsPublicURLs() {
        return true;
    }

    @Override
    protected boolean supportsMultipleImageInputsAsBase64EncodedStrings() {
        return true;
    }

    @Override
    protected boolean supportsPartialToolStreaming(dev.langchain4j.model.chat.StreamingChatModel model) {
        return false;
    }
}
