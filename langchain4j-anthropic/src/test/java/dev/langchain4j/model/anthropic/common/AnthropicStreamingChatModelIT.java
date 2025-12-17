package dev.langchain4j.model.anthropic.common;

import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_3_5_HAIKU_20241022;
import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_HAIKU_4_5_20251001;
import static java.lang.System.getenv;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;

import dev.langchain4j.model.anthropic.AnthropicChatResponseMetadata;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.anthropic.AnthropicTokenUsage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;

@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicStreamingChatModelIT extends AbstractStreamingChatModelIT {

    static final StreamingChatModel ANTHROPIC_STREAMING_CHAT_MODEL = AnthropicStreamingChatModel.builder()
            .apiKey(getenv("ANTHROPIC_API_KEY"))
            .modelName(CLAUDE_3_5_HAIKU_20241022)
            .temperature(0.0)
            .logRequests(false) // images are huge in logs
            .logResponses(true)
            .build();

    static final StreamingChatModel ANTHROPIC_STREAMING_SCHEMA_MODEL = AnthropicStreamingChatModel.builder()
            .apiKey(getenv("ANTHROPIC_API_KEY"))
            .modelName(CLAUDE_HAIKU_4_5_20251001)
            .beta("structured-outputs-2025-11-13")
            .temperature(0.0)
            .logRequests(false)
            .logResponses(true)
            .build();

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(ANTHROPIC_STREAMING_CHAT_MODEL);
    }

    @Override
    protected StreamingChatModel createModelWith(ChatRequestParameters parameters) {
        var anthropicChatModelBuilder = AnthropicStreamingChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .maxTokens(parameters.maxOutputTokens())
                .logRequests(true)
                .logResponses(true);
        if (parameters.modelName() == null) {
            anthropicChatModelBuilder.modelName(CLAUDE_3_5_HAIKU_20241022);
        } else {
            anthropicChatModelBuilder.modelName(parameters.modelName());
        }
        return anthropicChatModelBuilder.build();
    }

    @Override
    protected String customModelName() {
        return "claude-sonnet-4-5-20250929";
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return ChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(StreamingChatModel model) {
        return AnthropicTokenUsage.class;
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(StreamingChatModel model) {
        return AnthropicChatResponseMetadata.class;
    }

    @Override
    protected boolean supportsJsonResponseFormat() {
        // Anthropic does not support JSON response format without schemas yet
        return false;
    }

    @Override
    public StreamingChatModel createModelWith(ChatModelListener listener) {
        return AnthropicStreamingChatModel.builder()
                .apiKey(getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .listeners(List.of(listener))
                .build();
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id) {
        // Anthropic can talk before calling a tool. "atLeast(0)" is meant to ignore it.
        io.verify(handler, atLeast(0)).onPartialResponse(any(), any());

        io.verify(handler, atLeast(1)).onPartialToolCall(argThat(toolCall ->
                // Anthropic does not output same tokens consistently, so we can't easily assert partialArguments
                toolCall.index() == 0
                        && toolCall.id().equals(id)
                        && toolCall.name().equals("getWeather")
                        && !toolCall.partialArguments().isBlank()
        ), any());
        io.verify(handler).onCompleteToolCall(complete(0, id, "getWeather", "{\"city\": \"Munich\"}"));
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id1, String id2) {
        verifyToolCallbacks(handler, io, id1);

        io.verify(handler, atLeast(1)).onPartialToolCall(argThat(toolCall ->
                // Anthropic does not output same tokens consistently, so we can't easily assert partialArguments
                toolCall.index() == 1
                        && toolCall.id().equals(id2)
                        && toolCall.name().equals("getTime")
                        && !toolCall.partialArguments().isBlank()
        ), any());
        io.verify(handler).onCompleteToolCall(complete(1, id2, "getTime", "{\"country\": \"France\"}"));
    }

    @Override
    protected List<StreamingChatModel> modelsSupportingStructuredOutputs() {
        return List.of(ANTHROPIC_STREAMING_SCHEMA_MODEL);
    }

    @Override
    @ParameterizedTest
    @MethodSource("modelsSupportingStructuredOutputs")
    @EnabledIf("supportsToolsAndJsonResponseFormatWithSchema")
    protected void should_execute_a_tool_then_answer_respecting_JSON_response_format_with_schema(
            StreamingChatModel model) {
        // Anthropic structured outputs are a public beta and only supported by
        // Claude Sonnet 4.5, Opus 4.1/4.5, and Haiku 4.5 when the
        // 'structured-outputs-2025-11-13' beta header is enabled.
        super.should_execute_a_tool_then_answer_respecting_JSON_response_format_with_schema(model);
    }

    @Override
    @ParameterizedTest
    @MethodSource("modelsSupportingStructuredOutputs")
    @EnabledIf("supportsJsonResponseFormatWithRawSchema")
    protected void should_respect_JsonRawSchema_responseFormat(StreamingChatModel model) {
        // Anthropic structured outputs are a public beta and only supported by
        // Claude Sonnet 4.5, Opus 4.1/4.5, and Haiku 4.5 when the
        // 'structured-outputs-2025-11-13' beta header is enabled.
        super.should_respect_JsonRawSchema_responseFormat(model);
    }
}
