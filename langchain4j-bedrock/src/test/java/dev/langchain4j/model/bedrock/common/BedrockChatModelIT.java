package dev.langchain4j.model.bedrock.common;

import dev.langchain4j.model.bedrock.BedrockAnthropicMessageChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;

import java.util.List;

import static dev.langchain4j.model.bedrock.BedrockAnthropicMessageChatModel.Types.AnthropicClaude3HaikuV1;
import static dev.langchain4j.model.bedrock.BedrockAnthropicMessageChatModel.Types.AnthropicClaude3SonnetV1;

class BedrockChatModelIT extends AbstractChatModelIT {

    static final BedrockAnthropicMessageChatModel BEDROCK_ANTHROPIC_MESSAGE_CHAT_MODEL =
            BedrockAnthropicMessageChatModel.builder()
                    .model(AnthropicClaude3SonnetV1.getValue())
                    .build();

    @Override
    protected List<ChatLanguageModel> models() {
        // TODO add more models from other providers
        return List.of(BEDROCK_ANTHROPIC_MESSAGE_CHAT_MODEL);
    }

    @Override
    protected String modelName() {
        return AnthropicClaude3HaikuV1.getValue();
    }

    @Override
    protected boolean supportsModelNameParameter() {
        return false; // TODO implement
    }

    @Override
    protected boolean supportsTemperatureParameter() {
        return false; // TODO implement
    }

    @Override
    protected boolean supportsTopPParameter() {
        return false; // TODO implement
    }

    @Override
    protected boolean supportsTopKParameter() {
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
