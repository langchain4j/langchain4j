package dev.langchain4j.model.mistralai.common;

import static dev.langchain4j.model.mistralai.common.MistralAiStreamingChatModelIT.MISTRAL_STREAMING_CHAT_MODEL;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.common.AbstractStreamingAiServiceIT;
import java.util.List;

class MistralAiStreamingAiServiceIT extends AbstractStreamingAiServiceIT {

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(MISTRAL_STREAMING_CHAT_MODEL);
    }
}
