package dev.langchain4j.model.openaiofficial.openai;

import static dev.langchain4j.model.openaiofficial.openai.InternalOpenAiOfficialTestHelper.CHAT_MODEL_NAME_ALTERNATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openai.models.ChatModel;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.common.ChatResponseAndStreamingMetadata;
import dev.langchain4j.model.chat.common.StreamingMetadata;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatRequestParameters;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatResponseMetadata;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesStreamingChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiOfficialResponsesStreamingChatModelIT extends AbstractStreamingChatModelIT {

    @Override
    protected List<StreamingChatModel> models() {
        return InternalOpenAiOfficialTestHelper.chatModelsResponsesStreaming();
    }

    @Override
    protected StreamingChatModel createModelWith(ChatRequestParameters parameters) {
        OpenAiOfficialResponsesStreamingChatModel.Builder modelBuilder =
                InternalOpenAiOfficialTestHelper.responsesStreamingChatModelBuilder();

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
    public StreamingChatModel createModelWith(ChatModelListener listener) {
        return InternalOpenAiOfficialTestHelper.responsesStreamingChatModelBuilder()
                .modelName(InternalOpenAiOfficialTestHelper.CHAT_MODEL_NAME)
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
    @ParameterizedTest
    @MethodSource("modelsSupportingTools")
    @EnabledIf("supportsTools")
    protected void should_execute_multiple_tools_in_parallel_then_answer(StreamingChatModel model) {

        // given
        UserMessage userMessage = UserMessage.from(
                "What is the weather in Munich and time in France? Call tools simultaneously (in parallel)");

        ToolSpecification weatherTool = ToolSpecification.builder()
                .name("getWeather")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("city")
                        .build())
                .build();

        ToolSpecification timeTool = ToolSpecification.builder()
                .name("getTime")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("country")
                        .build())
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(weatherTool, timeTool)
                        .build())
                .build();

        // when
        ChatResponseAndStreamingMetadata responseAndMetadata = chat(model, chatRequest);
        ChatResponse chatResponse = responseAndMetadata.chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        List<ToolExecutionRequest> toolExecutionRequests = aiMessage.toolExecutionRequests();
        assertThat(toolExecutionRequests).hasSize(2);

        ToolExecutionRequest weatherToolExecutionRequest = toolExecutionRequests.stream()
                .filter(req -> "getWeather".equals(req.name()))
                .findFirst()
                .orElseThrow();
        assertThat(weatherToolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"city\":\"Munich\"}");

        ToolExecutionRequest timeToolExecutionRequest = toolExecutionRequests.stream()
                .filter(req -> "getTime".equals(req.name()))
                .findFirst()
                .orElseThrow();
        assertThat(timeToolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"country\":\"France\"}");

        if (assertToolId(model)) {
            assertThat(weatherToolExecutionRequest.id()).isNotBlank();
            assertThat(timeToolExecutionRequest.id())
                    .isNotBlank()
                    .isNotEqualTo(weatherToolExecutionRequest.id());
        }

        if (assertTokenUsage()) {
            TokenUsage tokenUsage = chatResponse.metadata().tokenUsage();
            assertThat(tokenUsage).isExactlyInstanceOf(tokenUsageType(model));
            assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
            assertThat(tokenUsage.totalTokenCount()).isGreaterThan(0);
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason())
                    .isEqualTo(dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION);
        }

        StreamingMetadata metadata = responseAndMetadata.streamingMetadata();
        assertThat(metadata.completeToolCalls()).hasSize(2);

        // Provider/tool-call ordering isn't guaranteed under "parallel", so verify content without assuming order.
        List<CompleteToolCall> completeToolCalls = metadata.completeToolCalls();
        assertThat(completeToolCalls).extracting(CompleteToolCall::index).containsExactlyInAnyOrder(0, 1);
        assertThat(completeToolCalls)
                .extracting(c -> c.toolExecutionRequest().name())
                .containsExactlyInAnyOrder("getWeather", "getTime");

        Optional<CompleteToolCall> completeWeatherToolCall = completeToolCalls.stream()
                .filter(c -> "getWeather".equals(c.toolExecutionRequest().name()))
                .findFirst();
        assertThat(completeWeatherToolCall).isPresent();
        assertThat(completeWeatherToolCall.orElseThrow().toolExecutionRequest().arguments())
                .isEqualToIgnoringWhitespace("{\"city\":\"Munich\"}");

        Optional<CompleteToolCall> completeTimeToolCall = completeToolCalls.stream()
                .filter(c -> "getTime".equals(c.toolExecutionRequest().name()))
                .findFirst();
        assertThat(completeTimeToolCall).isPresent();
        assertThat(completeTimeToolCall.orElseThrow().toolExecutionRequest().arguments())
                .isEqualToIgnoringWhitespace("{\"country\":\"France\"}");

        if (assertToolId(model)) {
            assertThat(completeWeatherToolCall.orElseThrow().toolExecutionRequest().id())
                    .isEqualTo(weatherToolExecutionRequest.id());
            assertThat(completeTimeToolCall.orElseThrow().toolExecutionRequest().id())
                    .isEqualTo(timeToolExecutionRequest.id());
        }

        // given
        ChatRequest chatRequest2 = ChatRequest.builder()
                .messages(
                        userMessage,
                        aiMessage,
                        ToolExecutionResultMessage.from(weatherToolExecutionRequest, "sunny"),
                        ToolExecutionResultMessage.from(timeToolExecutionRequest, "14:35"))
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(weatherTool, timeTool)
                        .build())
                .build();

        // when
        ChatResponseAndStreamingMetadata responseAndMetadata2 = chat(model, chatRequest2);
        ChatResponse chatResponse2 = responseAndMetadata2.chatResponse();

        // then
        AiMessage aiMessage2 = chatResponse2.aiMessage();
        assertThat(aiMessage2.text()).containsIgnoringCase("sun").contains("14", "35");
        assertThat(aiMessage2.toolExecutionRequests()).isEmpty();

        if (assertTokenUsage()) {
            TokenUsage tokenUsage = chatResponse2.metadata().tokenUsage();
            assertThat(tokenUsage).isExactlyInstanceOf(tokenUsageType(model));
            assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
            assertThat(tokenUsage.totalTokenCount()).isGreaterThan(0);
        }

        if (assertFinishReason()) {
            assertThat(chatResponse2.metadata().finishReason())
                    .isEqualTo(dev.langchain4j.model.output.FinishReason.STOP);
        }
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
        StreamingChatModel model = InternalOpenAiOfficialTestHelper.responsesStreamingChatModelBuilder()
                .modelName("o4-mini")
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
        StreamingChatModel model = InternalOpenAiOfficialTestHelper.responsesStreamingChatModelBuilder()
                .modelName(InternalOpenAiOfficialTestHelper.CHAT_MODEL_NAME)
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
        StreamingChatModel model = InternalOpenAiOfficialTestHelper.responsesStreamingChatModelBuilder()
                .modelName("o4-mini")
                .reasoningEffort(ReasoningEffort.of("medium"))
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("What is the capital of France?", handler);

        // then
        assertThat(handler.get().aiMessage().text()).contains("Paris");
    }

    @Test
    void should_support_reasoning_summary() {

        // given
        StreamingChatModel model = InternalOpenAiOfficialTestHelper.responsesStreamingChatModelBuilder()
                .modelName("o4-mini")
                .reasoningEffort(ReasoningEffort.of("medium"))
                .reasoningSummary(Reasoning.Summary.of("auto"))
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(
                "Do 2 plus 12, then 14 divided by 2, then multiply those results by 3. Think step by step.", handler);

        // then
        assertThat(handler.get().aiMessage().text()).isNotBlank();
        assertThat(handler.get().aiMessage().thinking()).isNotBlank();
    }

    @Test
    void should_not_return_reasoning_summary_when_not_requested() {

        // given
        StreamingChatModel model = InternalOpenAiOfficialTestHelper.responsesStreamingChatModelBuilder()
                .modelName(InternalOpenAiOfficialTestHelper.CHAT_MODEL_NAME)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("What is 2+2?", handler);

        // then
        assertThat(handler.get().aiMessage().text()).isNotBlank();
        assertThat(handler.get().aiMessage().thinking()).isNull();
    }

    @Test
    void should_support_max_tool_calls() {

        // given
        StreamingChatModel model = InternalOpenAiOfficialTestHelper.responsesStreamingChatModelBuilder()
                .modelName(InternalOpenAiOfficialTestHelper.CHAT_MODEL_NAME)
                .maxToolCalls(1)
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
        StreamingChatModel model = InternalOpenAiOfficialTestHelper.responsesStreamingChatModelBuilder()
                .modelName(InternalOpenAiOfficialTestHelper.CHAT_MODEL_NAME)
                .parallelToolCalls(false)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("Hello", handler);

        // then
        assertThat(handler.get().aiMessage().text()).isNotBlank();
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
    protected boolean supportsPartialToolStreaming(dev.langchain4j.model.chat.StreamingChatModel model) {
        return false;
    }
}
