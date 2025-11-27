package dev.langchain4j.model.vertexai.anthropic;

import static dev.langchain4j.model.vertexai.anthropic.VertexAiAnthropicFixtures.DEFAULT_LOCATION;
import static dev.langchain4j.model.vertexai.anthropic.VertexAiAnthropicFixtures.DEFAULT_MODEL_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.InOrder;

@EnabledIfEnvironmentVariable(named = "GCP_PROJECT_ID", matches = ".+")
class VertexAiAnthropicStreamingChatModelIT extends AbstractStreamingChatModelIT {

    static final StreamingChatModel VERTEX_AI_ANTHROPIC_STREAMING_CHAT_MODEL =
            VertexAiAnthropicStreamingChatModel.builder()
                    .project(System.getenv("GCP_PROJECT_ID"))
                    .location(DEFAULT_LOCATION)
                    .modelName(DEFAULT_MODEL_NAME)
                    .temperature(0.0)
                    .logRequests(false)
                    .logResponses(true)
                    .build();

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(VERTEX_AI_ANTHROPIC_STREAMING_CHAT_MODEL);
    }

    @Override
    protected StreamingChatModel createModelWith(ChatRequestParameters parameters) {
        VertexAiAnthropicStreamingChatModel.VertexAiAnthropicStreamingChatModelBuilder
                vertexAiAnthropicStreamingChatModelBuilder = VertexAiAnthropicStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(DEFAULT_LOCATION)
                .logRequests(true)
                .logResponses(true);
        if (parameters.modelName() == null) {
            vertexAiAnthropicStreamingChatModelBuilder.modelName(DEFAULT_MODEL_NAME);
        } else {
            vertexAiAnthropicStreamingChatModelBuilder.modelName(parameters.modelName());
        }
        // TODO support defaultRequestParameters
        if (parameters.temperature() != null) {
            vertexAiAnthropicStreamingChatModelBuilder.temperature(parameters.temperature());
        }
        if (parameters.topP() != null) {
            vertexAiAnthropicStreamingChatModelBuilder.topP(parameters.topP());
        }
        if (parameters.maxOutputTokens() != null) {
            vertexAiAnthropicStreamingChatModelBuilder.maxTokens(parameters.maxOutputTokens());
        }
        if (parameters.stopSequences() != null) {
            vertexAiAnthropicStreamingChatModelBuilder.stopSequences(parameters.stopSequences());
        }
        return vertexAiAnthropicStreamingChatModelBuilder.build();
    }

    @Override
    protected String customModelName() {
        return "claude-sonnet-4-5@20250929";
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return ChatRequestParameters.builder().maxOutputTokens(maxOutputTokens).build();
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(StreamingChatModel streamingChatModel) {
        return TokenUsage.class;
    }

    @Override
    protected boolean supportsJsonResponseFormat() {
        // Vertex AI Anthropic supports JSON response format through prompt engineering
        return false;
    }

    @Override
    protected boolean supportsJsonResponseFormatWithSchema() {
        // Vertex AI Anthropic does not support response format yet
        return false;
    }

    @Override
    protected boolean supportsJsonResponseFormatWithRawSchema() {
        // Vertex AI Anthropic does not support response format yet
        return false;
    }

    @Override
    protected boolean supportsSingleImageInputAsPublicURL() {
        // Vertex AI Anthropic does not support images as URLs, only as Base64-encoded strings
        return false;
    }

    @Override
    protected boolean supportsToolsAndJsonResponseFormatWithSchema() {
        // Vertex AI Anthropic does not support JSON response format with schema combined with tools yet
        return false;
    }

    @Override
    protected boolean supportsPartialToolStreaming(StreamingChatModel model) {
        // Vertex AI Anthropic does not support partial tool streaming
        return false;
    }

    @Override
    protected boolean supportsStreamingCancellation() {
        return false; // TODO implement
    }

    @Override
    public StreamingChatModel createModelWith(ChatModelListener listener) {
        return VertexAiAnthropicStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(DEFAULT_LOCATION)
                .modelName(DEFAULT_MODEL_NAME)
                .listeners(List.of(listener))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id) {
        // Vertex AI Anthropic can send text responses before tool calls
        // Use atLeast(0) to ignore any onPartialResponse calls that happen before tool execution
        io.verify(handler, atLeast(0)).onPartialResponse(any());

        // Vertex AI Anthropic doesn't support partial tool streaming, so we only verify onCompleteToolCall
        io.verify(handler).onCompleteToolCall(complete(0, id, "getWeather", "{\n  \"city\" : \"Munich\"\n}"));
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id1, String id2) {
        // Handle multiple tool calls - account for onPartialResponse calls first
        io.verify(handler, atLeast(0)).onPartialResponse(any());

        io.verify(handler).onCompleteToolCall(complete(0, id1, "getWeather", "{\n  \"city\" : \"Munich\"\n}"));
        io.verify(handler).onCompleteToolCall(complete(1, id2, "getTime", "{\n  \"country\" : \"France\"\n}"));
    }

    @Override
    protected boolean assertThreads() {
        return false; // Vertex AI Anthropic uses simulated streaming, not true async streaming
    }

    @Override
    protected boolean assertExceptionType() {
        return false; // Streaming exceptions get wrapped in RuntimeException/ExecutionException
    }
}
