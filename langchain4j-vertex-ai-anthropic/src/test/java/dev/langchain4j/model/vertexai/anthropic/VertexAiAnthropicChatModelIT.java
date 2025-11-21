package dev.langchain4j.model.vertexai.anthropic;

import static dev.langchain4j.model.vertexai.anthropic.VertexAiAnthropicFixtures.DEFAULT_MODEL_NAME;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GCP_PROJECT_ID", matches = ".+")
class VertexAiAnthropicChatModelIT extends AbstractChatModelIT {

    static final ChatModel VERTEX_AI_ANTHROPIC_CHAT_MODEL = VertexAiAnthropicChatModel.builder()
            .project(System.getenv("GCP_PROJECT_ID"))
            .location(System.getenv("GCP_LOCATION"))
            .modelName(DEFAULT_MODEL_NAME)
            .temperature(0.0)
            .logRequests(false)
            .logResponses(true)
            .build();

    @Override
    protected List<ChatModel> models() {
        return List.of(VERTEX_AI_ANTHROPIC_CHAT_MODEL);
    }

    @Override
    protected ChatModel createModelWith(ChatRequestParameters parameters) {
        VertexAiAnthropicChatModel.VertexAiAnthropicChatModelBuilder vertexAiAnthropicChatModelBuilder = VertexAiAnthropicChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .logRequests(true)
                .logResponses(true);
        if (parameters.modelName() == null) {
            vertexAiAnthropicChatModelBuilder.modelName(DEFAULT_MODEL_NAME);
        } else {
            vertexAiAnthropicChatModelBuilder.modelName(parameters.modelName());
        }
        if (parameters.temperature() != null) {
            vertexAiAnthropicChatModelBuilder.temperature(parameters.temperature());
        }
        if (parameters.topP() != null) {
            vertexAiAnthropicChatModelBuilder.topP(parameters.topP());
        }
        if (parameters.maxOutputTokens() != null) {
            vertexAiAnthropicChatModelBuilder.maxTokens(parameters.maxOutputTokens());
        }
        if (parameters.stopSequences() != null) {
            vertexAiAnthropicChatModelBuilder.stopSequences(parameters.stopSequences());
        }
        return vertexAiAnthropicChatModelBuilder.build();
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
    protected Class<? extends TokenUsage> tokenUsageType(ChatModel chatModel) {
        return TokenUsage.class;
    }

    @Override
    protected boolean supportsJsonResponseFormat() {
        // Vertex AI Anthropic supports JSON response format through prompt engineering
        return true;
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
    protected boolean supportsStopSequencesParameter() {
        return true;
    }

    @Override
    protected boolean supportsToolsAndJsonResponseFormatWithSchema() {
        return false;
    }
}
