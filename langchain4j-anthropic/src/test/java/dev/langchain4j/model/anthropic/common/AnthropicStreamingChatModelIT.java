package dev.langchain4j.model.anthropic.common;

import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_3_5_HAIKU_20241022;
import static dev.langchain4j.model.chat.common.AbstractChatModelAndCapabilities.SupportStatus.DISABLED;
import static dev.langchain4j.model.chat.common.AbstractChatModelAndCapabilities.SupportStatus.NOT_SUPPORTED;
import static java.lang.System.getenv;

import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelAndCapabilities;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.common.StreamingChatModelAndCapabilities;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicStreamingChatModelIT extends AbstractStreamingChatModelIT {

    static final StreamingChatLanguageModel ANTHROPIC_STREAMING_CHAT_MODEL = AnthropicStreamingChatModel.builder()
            .apiKey(getenv("ANTHROPIC_API_KEY"))
            .modelName(CLAUDE_3_5_HAIKU_20241022)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Override
    protected List<AbstractChatModelAndCapabilities<StreamingChatLanguageModel>> models() {
        return List.of(StreamingChatModelAndCapabilities.builder()
                .model(ANTHROPIC_STREAMING_CHAT_MODEL)
                .mnemonicName("anthropic_haiku_3_5")
                .supportsMaxOutputTokensParameter(NOT_SUPPORTED) // TODO implement
                .supportsModelNameParameter(NOT_SUPPORTED) // TODO implement
                .supportsSingleImageInputAsPublicURL(NOT_SUPPORTED)
                .supportsMultipleImageInputsAsBase64EncodedStrings(NOT_SUPPORTED)
                .supportsMultipleImageInputsAsPublicURLs(NOT_SUPPORTED)
                .supportsStopSequencesParameter(NOT_SUPPORTED) // TODO implement
                .supportsCommonParametersWrappedInIntegrationSpecificClass(DISABLED) // to be implemented
                .supportsJsonResponseFormatWithSchema(NOT_SUPPORTED)
                .supportsJsonResponseFormat(NOT_SUPPORTED)
                .supportsToolChoiceRequired(NOT_SUPPORTED) // TODO implement
                .assertExceptionType(false)
                .assertResponseId(false) // TODO implement
                .assertFinishReason(false)
                .assertResponseModel(false) // TODO implement
                .build());
    }

    @Override
    protected boolean disableParametersInDefaultModelTests() {
        return true; // TODO implement
    }
}
