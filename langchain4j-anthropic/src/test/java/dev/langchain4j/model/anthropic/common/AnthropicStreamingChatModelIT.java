package dev.langchain4j.model.anthropic.common;

import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_3_5_HAIKU_20241022;
import static java.lang.System.getenv;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;

import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.anthropic.AnthropicTokenUsage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import java.util.List;

import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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
        return "claude-3-5-sonnet-20241022";
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return ChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(StreamingChatModel streamingChatModel) {
        return AnthropicTokenUsage.class;
    }

    @Override
    protected boolean supportsJsonResponseFormat() {
        // Anthropic does not support response format yet
        return false;
    }

    @Override
    protected boolean supportsJsonResponseFormatWithSchema() {
        // Anthropic does not support response format yet
        return false;
    }

    @Override
    protected boolean supportsJsonResponseFormatWithRawSchema() {
        // Anthropic does not support response format yet
        return false;
    }

    @Override
    protected boolean supportsSingleImageInputAsPublicURL() {
        // Anthropic does not support images as URLs, only as Base64-encoded strings
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
        io.verify(handler, atLeast(0)).onPartialResponse(any());

        io.verify(handler, atLeast(1)).onPartialToolCall(argThat(toolCall ->
                // Anthropic does not output same tokens consistently, so we can't easily assert partialArguments
                toolCall.index() == 0
                        && toolCall.id().equals(id)
                        && toolCall.name().equals("getWeather")
                        && !toolCall.partialArguments().isBlank()
        ));
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
        ));
        io.verify(handler).onCompleteToolCall(complete(1, id2, "getTime", "{\"country\": \"France\"}"));
    }
}
