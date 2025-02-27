package dev.langchain4j.model.anthropic.common;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;

import java.util.List;

import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_3_5_HAIKU_20241022;

class AnthropicChatModelIT extends AbstractChatModelIT {

    static final ChatLanguageModel ANTHROPIC_CHAT_MODEL = AnthropicChatModel.builder()
            .apiKey(System.getenv("ANTHROPIC_API_KEY"))
            .modelName(CLAUDE_3_5_HAIKU_20241022)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(
                ANTHROPIC_CHAT_MODEL
        );
    }

    @Override
    protected boolean supportsDefaultRequestParameters() {
        return false;
    }

    @Override
    protected boolean supportsModelNameParameter() {
        return false;
    }

    @Override
    protected boolean supportsMaxOutputTokensParameter() {
        return false;
    }

    @Override
    protected boolean supportsStopSequencesParameter() {
        return false;
    }

    @Override
    protected boolean supportsToolChoiceRequiredWithMultipleTools() {
        return false;
    }

    @Override
    protected boolean supportsJsonResponseFormat() {
        return false;
    }

    @Override
    protected boolean supportsJsonResponseFormatWithSchema() {
        return false;
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
    protected boolean assertResponseId() {
        return false;
    }

    @Override
    protected boolean assertResponseModel() {
        return false;
    }
}
