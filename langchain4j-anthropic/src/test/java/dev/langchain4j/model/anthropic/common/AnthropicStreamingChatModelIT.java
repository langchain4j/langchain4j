package dev.langchain4j.model.anthropic.common;

import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;

import java.util.List;

import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_3_5_HAIKU_20241022;
import static java.lang.System.getenv;

class AnthropicStreamingChatModelIT extends AbstractStreamingChatModelIT {

    static final StreamingChatLanguageModel ANTHROPIC_STREAMING_CHAT_MODEL = AnthropicStreamingChatModel.builder()
            .apiKey(getenv("ANTHROPIC_API_KEY"))
            .modelName(CLAUDE_3_5_HAIKU_20241022)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Override
    protected List<StreamingChatLanguageModel> models() {
        return List.of(
                ANTHROPIC_STREAMING_CHAT_MODEL
        );
    }

    @Override
    protected boolean supportsDefaultRequestParameters() {
        return false; // TODO implement
    }

    @Override
    protected boolean supportsModelNameParameter() {
        return false; // TODO implement
    }

    @Override
    protected boolean supportsMaxOutputTokensParameter() {
        return false; // TODO implement
    }

    @Override
    protected boolean supportsStopSequencesParameter() {
        return false; // TODO implement
    }

    @Override
    protected boolean supportsToolChoiceRequiredWithMultipleTools() {
        return false; // TODO implement
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
        return false; // TODO implement
    }

    @Override
    protected boolean assertResponseModel() {
        return false; // TODO implement
    }
}
