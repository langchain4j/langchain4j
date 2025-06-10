package dev.langchain4j.model.mistralai.common;

import static dev.langchain4j.model.mistralai.MistralAiChatModelName.OPEN_MIXTRAL_8X22B;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.mistralai.MistralAiStreamingChatModel;
import java.util.List;

class MistralAiStreamingChatModelIT extends AbstractStreamingChatModelIT {

    static final StreamingChatModel MISTRAL_STREAMING_CHAT_MODEL = MistralAiStreamingChatModel.builder()
            .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
            .modelName(OPEN_MIXTRAL_8X22B)
            .temperature(0.0)
            .logRequests(false) // images are huge in logs
            .logResponses(true)
            .build();

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(MISTRAL_STREAMING_CHAT_MODEL);
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
    protected boolean supportsSingleImageInputAsBase64EncodedString() {
        return false; // TODO implement
    }

    @Override
    protected boolean supportsSingleImageInputAsPublicURL() {
        return false; // TODO implement
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
    protected boolean supportsToolsAndJsonResponseFormatWithSchema() {
        return false; // TODO implement
    }

    @Override
    public StreamingChatModel createModelWith(ChatModelListener listener) {
        return null; // TODO implement
    }
}
