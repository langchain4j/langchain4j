package dev.langchain4j.model.mistralai.common;

import static dev.langchain4j.model.mistralai.common.MistralAiStreamingChatModelIT.MISTRAL_STREAMING_CHAT_MODEL;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.common.AbstractStreamingAiServiceIT;
import org.junit.jupiter.api.Disabled;
import java.util.List;

class MistralAiStreamingAiServiceIT extends AbstractStreamingAiServiceIT {

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(MISTRAL_STREAMING_CHAT_MODEL);
    }

    @Override
    @Disabled("Mistral is too strict and expects assistant message after tool message")
    protected void should_keep_memory_consistent_when_streaming_using_immediate_tool(StreamingChatModel model) {}
}
