package dev.langchain4j.model.bedrock.common;

import dev.langchain4j.model.bedrock.BedrockAnthropicMessageChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT2;
import dev.langchain4j.model.chat.common.ChatLanguageModelCapabilities;
import dev.langchain4j.model.chat.common.ChatModelCapabilities;

import java.util.List;

import static dev.langchain4j.model.bedrock.BedrockAnthropicMessageChatModel.Types.AnthropicClaude3HaikuV1;
import static dev.langchain4j.model.bedrock.BedrockAnthropicMessageChatModel.Types.AnthropicClaude3SonnetV1;

class BedrockChatModelIT extends AbstractChatModelIT2 {

    // TODO https://github.com/langchain4j/langchain4j/issues/2219

    static final BedrockAnthropicMessageChatModel BEDROCK_ANTHROPIC_MESSAGE_CHAT_MODEL =
            BedrockAnthropicMessageChatModel.builder()
                    .model(AnthropicClaude3SonnetV1.getValue())
                    .build();

    @Override
    protected List<ChatModelCapabilities<ChatLanguageModel>> models() {
        return List.of(
                ChatLanguageModelCapabilities.builder()
                        .model(BEDROCK_ANTHROPIC_MESSAGE_CHAT_MODEL)
                        .supportsDefaultRequestParameters(false)
                        .supportsModelNameParameter(false)
                        .supportsMaxOutputTokensParameter(false)
                        .supportsStopSequencesParameter(false)
                        .supportsToolChoiceRequired(false)
                        .supportsJsonResponseFormat(false)
                        .supportsJsonResponseFormatWithSchema(false)
                        .supportsSingleImageInputAsPublicURL(false)
                        .assertResponseId(false)
                        .assertResponseModel(false)
                        .assertExceptionType(false)
                .build()
                // TODO add more models from other providers
                // TODO add more model configs, see OpenAiChatModelIT
        );
    }

    @Override
    protected String customModelName() {
        return AnthropicClaude3HaikuV1.getValue();
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
    protected boolean supportsToolChoiceRequired() {
        return false; // TODO implement
    }

    @Override
    protected boolean supportsJsonResponseFormat() {
        return false; // TODO check if supported
    }

    @Override
    protected boolean supportsJsonResponseFormatWithSchema() {
        return false; // TODO check if supported
    }

    @Override
    protected boolean supportsSingleImageInputAsPublicURL() {
        return false; // Bedrock supports only Base64-encoded images
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
