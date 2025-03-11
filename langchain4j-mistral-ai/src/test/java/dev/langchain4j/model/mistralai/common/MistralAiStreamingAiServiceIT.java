package dev.langchain4j.model.mistralai.common;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.common.AbstractStreamingAiServiceIT;

import java.util.List;

import static dev.langchain4j.model.mistralai.common.MistralAiStreamingChatModelIT.MISTRAL_STREAMING_CHAT_MODEL;

class MistralAiStreamingAiServiceIT extends AbstractStreamingAiServiceIT {

    @Override
    protected List<StreamingChatLanguageModel> models() {
        return List.of(
                MISTRAL_STREAMING_CHAT_MODEL
        );
    }
}
