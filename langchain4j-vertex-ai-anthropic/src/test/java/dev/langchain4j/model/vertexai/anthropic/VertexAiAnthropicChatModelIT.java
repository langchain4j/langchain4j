package dev.langchain4j.model.vertexai.anthropic;

import static dev.langchain4j.model.vertexai.anthropic.VertexAiAnthropicFixtures.DEFAULT_LOCATION;
import static dev.langchain4j.model.vertexai.anthropic.VertexAiAnthropicFixtures.DEFAULT_MODEL_NAME;
import static java.time.DayOfWeek.MONDAY;
import static org.junit.jupiter.api.condition.JRE.JAVA_17;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.output.TokenUsage;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledOnJre;

@EnabledIf(value = "isMonday", disabledReason = "Not enough quota to run it more often")
@EnabledOnJre(value = JAVA_17, disabledReason = "Not enough quota to run it more often")
@EnabledIfEnvironmentVariable(named = "GCP_PROJECT_ID", matches = ".+")
class VertexAiAnthropicChatModelIT extends AbstractChatModelIT {

    private final ChatModel model = VertexAiAnthropicChatModel.builder()
            .project(System.getenv("GCP_PROJECT_ID"))
            .location(DEFAULT_LOCATION)
            .modelName(DEFAULT_MODEL_NAME)
            .temperature(0.0)
            .logRequests(false)
            .logResponses(true)
            .build();

    @Override
    protected List<ChatModel> models() {
        return List.of(model);
    }

    @Override
    protected ChatModel createModelWith(ChatRequestParameters parameters) {
        VertexAiAnthropicChatModel.VertexAiAnthropicChatModelBuilder vertexAiAnthropicChatModelBuilder = VertexAiAnthropicChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(DEFAULT_LOCATION)
                .logRequests(true)
                .logResponses(true);
        if (parameters.modelName() == null) {
            vertexAiAnthropicChatModelBuilder.modelName(DEFAULT_MODEL_NAME);
        } else {
            vertexAiAnthropicChatModelBuilder.modelName(parameters.modelName());
        }
        // TODO support defaultRequestParameters
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
        return "claude-sonnet-4-5@20250929";
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
        return true; // TODO
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
        return false;
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_VERTEX_AI_ANTHROPIC");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }

    public static boolean isMonday() {
        return LocalDate.now().getDayOfWeek() == MONDAY;
    }
}
