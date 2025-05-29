package dev.langchain4j.model.googleai.common;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.common.AbstractStreamingAiServiceIT;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static dev.langchain4j.model.googleai.common.GoogleAiGeminiStreamingChatModelIT.GOOGLE_AI_GEMINI_STREAMING_CHAT_MODEL;

class GoogleAiGeminiStreamingAiServiceIT extends AbstractStreamingAiServiceIT {

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(
                GOOGLE_AI_GEMINI_STREAMING_CHAT_MODEL
        );
    }

    @Disabled("Gemini cannot do it properly")
    @Override
    @ParameterizedTest
    @MethodSource("models")
    protected void should_execute_tool_without_arguments(StreamingChatModel model) {
    }
}
