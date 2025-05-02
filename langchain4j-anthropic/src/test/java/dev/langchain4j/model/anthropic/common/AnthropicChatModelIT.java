package dev.langchain4j.model.anthropic.common;

import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_3_5_HAIKU_20241022;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicTokenUsage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicChatModelIT extends AbstractChatModelIT {

    static final ChatModel ANTHROPIC_CHAT_MODEL = AnthropicChatModel.builder()
            .apiKey(System.getenv("ANTHROPIC_API_KEY"))
            .modelName(CLAUDE_3_5_HAIKU_20241022)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Override
    protected List<ChatModel> models() {
        return List.of(ANTHROPIC_CHAT_MODEL);
    }

    @Override
    protected boolean supportsDefaultRequestParameters() {
        return false; // TODO implement
    }

    protected static boolean supportsModelNameParameter() {
        return false; // TODO implement
    }

    protected static boolean supportsMaxOutputTokensParameter() {
        return false; // TODO implement
    }

    protected static boolean supportsStopSequencesParameter() {
        return false; // TODO implement
    }

    protected static boolean supportsToolChoiceRequired() {
        return false; // TODO implement
    }

    protected static boolean supportsJsonResponseFormat() {
        return false;
    }

    protected static boolean supportsJsonResponseFormatWithSchema() {
        return false;
    }

    protected static boolean supportsSingleImageInputAsPublicURL() {
        return false;
    }

    protected static boolean supportsMultipleImageInputsAsPublicURLs() {
        return false;
    }

    @Override
    protected boolean assertResponseId() {
        return false; // TODO implement
    }

    @Override
    protected boolean assertResponseModel() {
        return false; // TODO implement
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType() {
        return AnthropicTokenUsage.class;
    }
}
