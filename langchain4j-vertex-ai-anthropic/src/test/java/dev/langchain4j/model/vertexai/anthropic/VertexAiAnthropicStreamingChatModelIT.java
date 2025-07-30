package dev.langchain4j.model.vertexai.anthropic;

import static dev.langchain4j.model.vertexai.anthropic.VertexAiAnthropicFixtures.DEFAULT_MODEL_NAME;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GCP_PROJECT_ID", matches = ".+")
class VertexAiAnthropicStreamingChatModelIT extends AbstractStreamingChatModelIT {

    static final StreamingChatModel VERTEX_AI_ANTHROPIC_STREAMING_CHAT_MODEL =
            VertexAiAnthropicStreamingChatModel.builder()
                    .project(System.getenv("GCP_PROJECT_ID"))
                    .location(System.getenv("GCP_LOCATION"))
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
        var vertexAiAnthropicStreamingChatModelBuilder = VertexAiAnthropicStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .logRequests(true)
                .logResponses(true);
        if (parameters.modelName() == null) {
            vertexAiAnthropicStreamingChatModelBuilder.modelName(DEFAULT_MODEL_NAME);
        } else {
            vertexAiAnthropicStreamingChatModelBuilder.modelName(parameters.modelName());
        }
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
        return "claude-3-5-sonnet-v2@20241022";
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
    protected boolean supportsSingleImageInputAsPublicURL() {
        // Vertex AI Anthropic does not support images as URLs, only as Base64-encoded strings
        return false;
    }

    @Override
    protected boolean supportsStopSequencesParameter() {
        return true;
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
    public StreamingChatModel createModelWith(ChatModelListener listener) {
        return VertexAiAnthropicStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(DEFAULT_MODEL_NAME)
                .listeners(List.of(listener))
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}
