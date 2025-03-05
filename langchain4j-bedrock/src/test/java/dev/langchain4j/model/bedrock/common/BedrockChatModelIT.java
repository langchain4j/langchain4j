package dev.langchain4j.model.bedrock.common;

import static dev.langchain4j.model.bedrock.BedrockAnthropicMessageChatModel.Types.AnthropicClaude3HaikuV1;
import static dev.langchain4j.model.bedrock.BedrockAnthropicMessageChatModel.Types.AnthropicClaude3SonnetV1;
import static dev.langchain4j.model.chat.common.AbstractChatModelAndCapabilities.SupportStatus.NOT_SUPPORTED;

import dev.langchain4j.model.bedrock.BedrockAnthropicMessageChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelAndCapabilities;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.common.ChatModelAndCapabilities;
import java.util.List;

class BedrockChatModelIT extends AbstractChatModelIT {

    // TODO https://github.com/langchain4j/langchain4j/issues/2219

    static final BedrockAnthropicMessageChatModel BEDROCK_ANTHROPIC_MESSAGE_CHAT_MODEL =
            BedrockAnthropicMessageChatModel.builder()
                    .model(AnthropicClaude3SonnetV1.getValue())
                    .build();

    @Override
    protected List<AbstractChatModelAndCapabilities<ChatLanguageModel>> models() {
        return List.of(
                ChatModelAndCapabilities.builder()
                        .model(BEDROCK_ANTHROPIC_MESSAGE_CHAT_MODEL)
                        .supportsDefaultRequestParameters(NOT_SUPPORTED)
                        .supportsModelNameParameter(NOT_SUPPORTED)
                        .supportsMaxOutputTokensParameter(NOT_SUPPORTED)
                        .supportsStopSequencesParameter(NOT_SUPPORTED)
                        .supportsToolChoiceRequired(NOT_SUPPORTED)
                        .supportsJsonResponseFormat(NOT_SUPPORTED)
                        .supportsJsonResponseFormatWithSchema(NOT_SUPPORTED)
                        .supportsSingleImageInputAsPublicURL(NOT_SUPPORTED)
                        .supportsCommonParametersWrappedInIntegrationSpecificClass(NOT_SUPPORTED)
                        .assertResponseId(false)
                        .assertResponseModel(false)
                        .assertExceptionType(false)
                        .build()
                // TODO add more models from other providers
                // TODO add more model configs, see OpenAiChatModelIT
                );
    }

    @Override
    protected boolean disableParametersInDefaultModelTests() {
        return true; // TODO implement
    }

    @Override
    protected String customModelName() {
        return AnthropicClaude3HaikuV1.getValue();
    }
}
